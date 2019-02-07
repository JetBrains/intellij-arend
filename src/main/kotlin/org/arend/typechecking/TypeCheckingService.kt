package org.arend.typechecking

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.*
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType
import org.arend.core.definition.Definition
import org.arend.error.DummyErrorReporter
import org.arend.library.LibraryManager
import org.arend.module.ArendPreludeLibrary
import org.arend.module.ArendRawLibrary
import org.arend.module.ModulePath
import org.arend.module.util.defaultRoot
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
import org.arend.util.FileUtils

interface TypeCheckingService {
    val libraryManager: LibraryManager

    val typecheckerState: TypecheckerState

    val dependencyListener: DependencyListener

    val project: Project

    val prelude: ArendFile?

    val updatedModules: HashSet<ModulePath>

    fun initialize()

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

    init {
        VirtualFileManager.getInstance().addVirtualFileListener(MyVirtualFileListener(), project)
    }

    private var isInitialized = false

    override fun initialize() {
        if (isInitialized) {
            return
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
        val tcReferable = simpleReferableConverter.remove(referable)?.typecheckable ?: return null
        tcReferable.location?.let { updatedModules.add(it) }
        if (referable is ClassReferable) {
            for (field in referable.fieldReferables) {
                simpleReferableConverter.remove(field)
            }
        } else if (referable is DataDefinitionAdapter) {
            for (constructor in referable.constructors) {
                simpleReferableConverter.remove(constructor)
            }
        }
        return tcReferable
    }

    override fun updateDefinition(referable: LocatedReferable) {
        val tcReferable = removeDefinition(referable) ?: return
        for (ref in dependencyListener.update(tcReferable)) {
            removeDefinition(ref)
        }
    }

    private inner class MyVirtualFileListener : VirtualFileListener {
        override fun beforeFileDeletion(event: VirtualFileEvent) {
            process(event, event.fileName, event.parent, null)
        }

        override fun fileCreated(event: VirtualFileEvent) {
            process(event, event.fileName, null, event.parent)
        }

        private fun process(event: VirtualFileEvent, fileName: String, oldParent: VirtualFile?, newParent: VirtualFile?) {
            if (oldParent == null && newParent == null) {
                return
            }
            if (fileName == FileUtils.LIBRARY_CONFIG_FILE) {
                val module = ModuleUtil.findModuleForFile(event.file, project) ?: return
                val root = module.defaultRoot
                if (root != null && root == oldParent) {
                    libraryManager.getRegisteredLibrary(module.name)?.let { libraryManager.unloadLibrary(it) }
                }
                if (root != null && root == newParent) {
                    libraryManager.loadLibrary(ArendRawLibrary(module, typecheckerState))
                }
            }
        }

        override fun fileMoved(event: VirtualFileMoveEvent) {
            process(event, event.fileName, event.oldParent, event.newParent)
        }

        override fun fileCopied(event: VirtualFileCopyEvent) {
            process(event, event.fileName, null, event.parent)
        }

        override fun propertyChanged(event: VirtualFilePropertyEvent) {
            if (event.propertyName == VirtualFile.PROP_NAME) {
                process(event, event.oldValue as String, event.parent, null)
                process(event, event.newValue as String, null, event.parent)
            }
        }
    }

    private inner class TypeCheckerPsiTreeChangeListener : PsiTreeChangeAdapter() {
         override fun beforeChildrenChange(event: PsiTreeChangeEvent) {
            processParent(event, false)
        }

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
                child is LeafPsiElement && isComment(child.node.elementType)) {
                return
            }
            val oldChild = event.oldChild
            val newChild = event.newChild
            if (oldChild is PsiWhiteSpace && newChild is PsiWhiteSpace ||
                (oldChild is ArendWhere || oldChild is PsiErrorElement || isDynamicDef(oldChild)) && (newChild is ArendWhere || newChild is PsiErrorElement || isDynamicDef(newChild)) ||
                oldChild is LeafPsiElement && isComment(oldChild.node.elementType) && newChild is LeafPsiElement && isComment(newChild.node.elementType)) {
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

private fun isComment(element: IElementType) = element == ArendElementTypes.BLOCK_COMMENT || element == ArendElementTypes.LINE_COMMENT
