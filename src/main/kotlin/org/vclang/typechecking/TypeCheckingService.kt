package org.vclang.typechecking

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.AnyPsiChangeListener
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.jetbrains.jetpad.vclang.core.definition.Definition
import com.jetbrains.jetpad.vclang.library.LibraryManager
import com.jetbrains.jetpad.vclang.module.scopeprovider.EmptyModuleScopeProvider
import com.jetbrains.jetpad.vclang.module.scopeprovider.LocatingModuleScopeProvider
import com.jetbrains.jetpad.vclang.naming.reference.LocatedReferable
import com.jetbrains.jetpad.vclang.naming.reference.converter.ReferableConverter
import com.jetbrains.jetpad.vclang.naming.reference.converter.SimpleReferableConverter
import com.jetbrains.jetpad.vclang.term.prettyprint.PrettyPrinterConfig
import com.jetbrains.jetpad.vclang.typechecking.SimpleTypecheckerState
import com.jetbrains.jetpad.vclang.typechecking.TypecheckerState
import com.jetbrains.jetpad.vclang.typechecking.order.dependency.DependencyCollector
import com.jetbrains.jetpad.vclang.typechecking.order.dependency.DependencyListener
import org.vclang.psi.*
import org.vclang.psi.ext.PsiLocatedReferable
import org.vclang.resolving.VcReferableConverter
import org.vclang.resolving.VcResolveCache
import org.vclang.typechecking.error.LogErrorReporter

interface TypeCheckingService {
    val libraryManager: LibraryManager

    val typecheckerState: TypecheckerState

    val dependencyListener: DependencyListener

    val referableConverter: ReferableConverter

    fun getTypechecked(definition: VcDefinition): Definition?

    fun updateDefinition(referable: LocatedReferable)

    companion object {
        fun getInstance(project: Project): TypeCheckingService {
            val service = ServiceManager.getService(project, TypeCheckingService::class.java)
            return checkNotNull(service) { "Failed to get TypeCheckingService for $project" }
        }
    }
}

class TypeCheckingServiceImpl(private val project: Project) : TypeCheckingService {
    override val typecheckerState = SimpleTypecheckerState()
    override val dependencyListener = DependencyCollector(typecheckerState)
    private val libraryErrorReporter = LogErrorReporter(PrettyPrinterConfig.DEFAULT)
    override val libraryManager = LibraryManager(VcLibraryResolver(project), EmptyModuleScopeProvider.INSTANCE, null, libraryErrorReporter, libraryErrorReporter)

    private val simpleReferableConverter = SimpleReferableConverter()
    override val referableConverter: ReferableConverter
        get() = VcReferableConverter(project, simpleReferableConverter)

    init {
        libraryManager.moduleScopeProvider = LocatingModuleScopeProvider(libraryManager)

        PsiManager.getInstance(project).addPsiTreeChangeListener(TypeCheckerPsiTreeChangeListener())
        project.messageBus.connect(project).subscribe(PsiManagerImpl.ANY_PSI_CHANGE_TOPIC, object : AnyPsiChangeListener.Adapter() {
            override fun beforePsiChanged(isPhysical: Boolean) {
                VcResolveCache.clearCache()
            }
        })
    }

    override fun getTypechecked(definition: VcDefinition) =
        simpleReferableConverter.toDataLocatedReferable(definition)?.let { typecheckerState.getTypechecked(it) }

    override fun updateDefinition(referable: LocatedReferable) {
        simpleReferableConverter.remove(referable)?.let {
            for (ref in dependencyListener.update(it)) {
                PsiLocatedReferable.fromReferable(ref)?.let { simpleReferableConverter.remove(it) }
            }
        }
    }

    private inner class TypeCheckerPsiTreeChangeListener : PsiTreeChangeAdapter() {
         override fun beforeChildrenChange(event: PsiTreeChangeEvent) {
            processParent(event)
        }

        override fun beforeChildAddition(event: PsiTreeChangeEvent) {
            processParent(event)
        }

        override fun beforeChildReplacement(event: PsiTreeChangeEvent) {
            processParent(event)
        }

        override fun beforeChildMovement(event: PsiTreeChangeEvent) {
            processParent(event)
        }

        override fun beforeChildRemoval(event: PsiTreeChangeEvent) {
            if (event.child is VcFile) { // whole file has been removed
                for (child in event.child.children) invalidateChild(child)
            } else {
                processChildren(event)
                processParent(event)
            }
        }

        private fun processParent(event: PsiTreeChangeEvent) {
            if (event.file !is VcFile) {
                return
            }
            val child = event.child
            if (child is PsiErrorElement ||
                child is PsiWhiteSpace ||
                child is LeafPsiElement && child.node.elementType == VcElementTypes.BLOCK_COMMENT) {
                return
            }
            val oldChild = event.oldChild
            val newChild = event.newChild
            if (oldChild is PsiErrorElement && newChild is PsiErrorElement ||
                oldChild is PsiWhiteSpace && newChild is PsiWhiteSpace ||
                oldChild is LeafPsiElement && oldChild.node.elementType == VcElementTypes.BLOCK_COMMENT && newChild is LeafPsiElement && newChild.node.elementType == VcElementTypes.BLOCK_COMMENT) {
                return
            }

            val ancestors = event.parent.ancestors
            val definition = ancestors.filterIsInstance<VcDefinition>().firstOrNull() ?: return
            updateDefinition(definition)
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
            if (event.file is VcFile) {
                invalidateChild(event.child)
            }
        }
    }
}
