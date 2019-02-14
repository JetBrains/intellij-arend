package org.arend.typechecking

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.arend.core.definition.Definition
import org.arend.error.DummyErrorReporter
import org.arend.library.LibraryManager
import org.arend.module.ArendPreludeLibrary
import org.arend.module.ModulePath
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.TCReferable
import org.arend.naming.reference.converter.SimpleReferableConverter
import org.arend.prelude.Prelude
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.impl.DataDefinitionAdapter
import org.arend.resolving.ArendReferableConverter
import org.arend.resolving.PsiConcreteProvider
import org.arend.term.prettyprint.PrettyPrinterConfig
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.typechecking.order.dependency.DependencyCollector
import org.arend.typechecking.order.dependency.DependencyListener

interface TypeCheckingService {
    val libraryManager: LibraryManager

    val typecheckerState: TypecheckerState

    val dependencyListener: DependencyListener

    val project: Project

    val prelude: ArendFile?

    val updatedModules: HashSet<ModulePath>

    fun initialize(): Boolean

    fun newReferableConverter(withPsiReferences: Boolean): ArendReferableConverter

    fun getTypechecked(definition: ArendDefinition): Definition?

    fun updateDefinition(referable: LocatedReferable)

    companion object {
        fun getInstance(project: Project): TypeCheckingService {
            val service = ServiceManager.getService(project, TypeCheckingService::class.java)
            return checkNotNull(service) { "Failed to get TypeCheckingService for $project" }
        }
    }
}

class TypeCheckingServiceImpl(override val project: Project) : TypeCheckingService {
    override val typecheckerState = SimpleTypecheckerState()
    override val dependencyListener = DependencyCollector(typecheckerState)
    private val libraryErrorReporter = NotificationErrorReporter(project, PrettyPrinterConfig.DEFAULT)
    override val libraryManager = LibraryManager(ArendLibraryResolver(project), null, libraryErrorReporter, libraryErrorReporter)

    private val simpleReferableConverter = SimpleReferableConverter()

    override val updatedModules = HashSet<ModulePath>()

    override fun newReferableConverter(withPsiReferences: Boolean) =
        ArendReferableConverter(if (withPsiReferences) project else null, simpleReferableConverter)

    private var isInitialized = false

    override fun initialize(): Boolean {
        if (isInitialized) {
            return false
        }

        // Initialize prelude
        val preludeLibrary = ArendPreludeLibrary(project, typecheckerState)
        libraryManager.loadLibrary(preludeLibrary)
        val referableConverter = newReferableConverter(false)
        val concreteProvider = PsiConcreteProvider(project, referableConverter, DummyErrorReporter.INSTANCE, null)
        preludeLibrary.resolveNames(referableConverter, concreteProvider, libraryManager.libraryErrorReporter)
        Prelude.PreludeTypechecking(PsiInstanceProviderSet(concreteProvider, referableConverter), typecheckerState, concreteProvider).typecheckLibrary(preludeLibrary)

        // Set the listener that updates typechecked definitions
        PsiManager.getInstance(project).addPsiTreeChangeListener(TypeCheckerPsiTreeChangeListener())

        isInitialized = true
        return true
    }

    override val prelude: ArendFile?
        get() {
            for (library in libraryManager.registeredLibraries) {
                if (library is ArendPreludeLibrary) {
                    return library.prelude
                }
            }
            return null
        }

    override fun getTypechecked(definition: ArendDefinition) =
        simpleReferableConverter.toDataLocatedReferable(definition)?.let { typecheckerState.getTypechecked(it) }

    private fun removeDefinition(referable: LocatedReferable): TCReferable? {
        val tcReferable = simpleReferableConverter.remove(referable) ?: return null
        val data = tcReferable.data
        if (data is SmartPsiElementPointer<*>) {
            val element = data.element
            if (element is LocatedReferable && element != referable) {
                simpleReferableConverter.putIfAbsent(referable, tcReferable)
                return null
            }
        }

        val tcTypecheckable = tcReferable.typecheckable ?: return null
        tcTypecheckable.location?.let { updatedModules.add(it) }
        if (referable is ClassReferable) {
            for (field in referable.fieldReferables) {
                simpleReferableConverter.remove(field)
            }
        } else if (referable is DataDefinitionAdapter) {
            for (constructor in referable.constructors) {
                simpleReferableConverter.remove(constructor)
            }
        }
        return tcTypecheckable
    }

    override fun updateDefinition(referable: LocatedReferable) {
        val tcReferable = removeDefinition(referable) ?: return
        for (ref in dependencyListener.update(tcReferable)) {
            removeDefinition(ref)
        }
    }

    private inner class TypeCheckerPsiTreeChangeListener : PsiTreeChangeAdapter() {
        override fun beforeChildAddition(event: PsiTreeChangeEvent) {
            processParent(event, true)
        }

        override fun beforeChildReplacement(event: PsiTreeChangeEvent) {
            processParent(event, false)
        }

        override fun beforeChildMovement(event: PsiTreeChangeEvent) {
            processParent(event, false)
        }

        override fun beforeChildRemoval(event: PsiTreeChangeEvent) {
            if (event.child is ArendFile) { // whole file has been removed
                for (child in event.child.children) invalidateChild(child)
            } else {
                processChildren(event)
                processParent(event, true)
            }
        }

        private fun isDynamicDef(elem: PsiElement?) = elem is ArendClassStat && (elem.definition != null || elem.defModule != null)

        private fun processParent(event: PsiTreeChangeEvent, checkCommentStart: Boolean) {
            if (event.file !is ArendFile) {
                return
            }
            val child = event.child
            if (child is PsiErrorElement ||
                child is PsiWhiteSpace ||
                child is ArendWhere ||
                isDynamicDef(child) ||
                child is LeafPsiElement && AREND_COMMENTS.contains(child.node.elementType)) {
                return
            }
            val oldChild = event.oldChild
            val newChild = event.newChild
            if (oldChild is PsiWhiteSpace && newChild is PsiWhiteSpace ||
                (oldChild is ArendWhere || oldChild is PsiErrorElement || isDynamicDef(oldChild)) && (newChild is ArendWhere || newChild is PsiErrorElement || isDynamicDef(newChild)) ||
                oldChild is LeafPsiElement && AREND_COMMENTS.contains(oldChild.node.elementType) && newChild is LeafPsiElement && AREND_COMMENTS.contains(newChild.node.elementType)) {
                return
            }

            if (checkCommentStart) {
                var node = (child as? ArendCompositeElement)?.node ?: child as? LeafPsiElement
                while (node != null && node !is LeafPsiElement) {
                    val first = node.firstChildNode
                    if (first == null || node.lastChildNode != first) {
                        break
                    }
                    node = first
                }
                if (node is LeafPsiElement && node.textLength == 1) {
                    val ch = node.charAt(0)
                    if (ch == '-' || ch == '{' || ch == '}') {
                        return
                    }
                }
            }

            var elem = event.parent
            while (elem != null) {
                if (elem is ArendWhere || elem is ArendFile || isDynamicDef(elem)) {
                    return
                }
                if (elem is ArendDefinition) {
                    updateDefinition(elem)
                    return
                }
                elem = elem.parent
            }
        }

        private fun invalidateChild(element : PsiElement) {
            element.accept(object : PsiRecursiveElementVisitor() {
                override fun visitElement(element: PsiElement?) {
                    super.visitElement(element)
                    if (element is LocatedReferable) {
                        updateDefinition(element)
                    }
                }
            })
        }

        private fun processChildren(event: PsiTreeChangeEvent) {
            if (event.file is ArendFile) {
                invalidateChild(event.child)
            }
        }
    }
}
