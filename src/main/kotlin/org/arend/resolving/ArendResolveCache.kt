package org.arend.resolving

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.containers.ContainerUtil
import org.arend.naming.reference.Referable
import org.arend.naming.reference.TCClassReferable
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.ArendSourceNode
import org.arend.typechecking.TypeCheckingService
import java.util.concurrent.ConcurrentMap

interface ArendResolveCache {
    fun resolveCached(resolver: (ArendReferenceElement) -> Referable?, ref : ArendReferenceElement) : Referable?
    fun processEvent(oldChild: PsiElement?, newChild: PsiElement?, parent: PsiElement?)
    fun clear()
}

private fun getDefinitionOfLocalElement(element: PsiElement) =
    (element as? ArendCompositeElement)?.ancestorsUntilFile?.firstOrNull { it is ArendDefinition || it is ArendStatCmd || it is ArendDefModule } as? ArendDefinition

class ArendResolveCacheImpl(private val project: Project) : ArendResolveCache {
    private val globalMap: ConcurrentMap<ArendReferenceElement, Referable> = ContainerUtil.createConcurrentWeakKeySoftValueMap()
    private val localMap: ConcurrentMap<ArendDefinition, HashMap<ArendReferenceElement, Referable>> = ContainerUtil.createConcurrentWeakKeySoftValueMap()
    private val listener = ResolveCacheCleaner()

    override fun resolveCached(resolver: (ArendReferenceElement) -> Referable?, ref: ArendReferenceElement): Referable? {
        val globalRef = globalMap[ref]
        if (globalRef != null) {
            return if (globalRef == TCClassReferable.NULL_REFERABLE) null else globalRef
        }

        val def = getDefinitionOfLocalElement(ref)
        val defMap = if (def is ArendDefinition) localMap.computeIfAbsent(def) { HashMap() } else null
        if (defMap != null) {
            val localRef = defMap[ref]
            if (localRef != null) {
                return if (localRef == TCClassReferable.NULL_REFERABLE) null else localRef
            }
        }

        val result = resolver(ref)
        if (result == null && !TypeCheckingService.getInstance(project).isInitialized) {
            return null
        }

        val cachedResult = result ?: TCClassReferable.NULL_REFERABLE
        if (defMap != null) {
            defMap[ref] = cachedResult
        } else {
            globalMap[ref] = cachedResult
        }
        return result
    }

    init {
        PsiManager.getInstance(project).addPsiTreeChangeListener(listener)
    }

    override fun processEvent(oldChild: PsiElement?, newChild: PsiElement?, parent: PsiElement?) {
        listener.update(oldChild, newChild, parent)
    }

    override fun clear() {
        globalMap.clear()
        localMap.clear()
    }

    private inner class ResolveCacheCleaner : PsiTreeChangeAdapter() {
        override fun beforeChildReplacement(event: PsiTreeChangeEvent) {
            if (event.file is ArendFile) update(event.oldChild, event.newChild, event.parent)
        }

        override fun beforeChildMovement(event: PsiTreeChangeEvent) {
            if (event.file is ArendFile) update(event.child, null, event.oldParent)
        }

        override fun beforeChildRemoval(event: PsiTreeChangeEvent) {
            if (event.file is ArendFile) update(event.child, null, event.parent)
        }

        override fun beforeChildAddition(event: PsiTreeChangeEvent) {
            if (event.file is ArendFile) update(event.child, null, event.parent)
        }

        fun update(oldChild: PsiElement?, newChild: PsiElement?, parent: PsiElement?) {
            if ((oldChild == null ||
                 oldChild is PsiErrorElement ||
                 oldChild is PsiWhiteSpace ||
                 oldChild is LeafPsiElement && AREND_COMMENTS.contains(oldChild.node.elementType)) &&
                (newChild == null ||
                 newChild is PsiErrorElement ||
                 newChild is PsiWhiteSpace ||
                 newChild is LeafPsiElement && AREND_COMMENTS.contains(newChild.node.elementType))) {
                return
            }

            val sourceNode = (parent as? ArendCompositeElement)?.ancestorsUntilFile?.firstOrNull { it is ArendSourceNode } as? ArendSourceNode ?: return
            if (sourceNode.isLocal) {
                if (sourceNode is ArendLongName) {
                    for (refIdentifier in sourceNode.refIdentifierList) {
                        globalMap.remove(refIdentifier)
                    }
                }

                val def = getDefinitionOfLocalElement(sourceNode)
                if (def != null) {
                    localMap.remove(def)
                    return
                }
            }

            globalMap.clear()
            localMap.clear()
        }
    }
}