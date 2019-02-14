package org.arend.resolving

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.containers.ContainerUtil
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.Referable
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.ArendSourceNode
import java.util.concurrent.ConcurrentMap

interface ArendResolveCache {
    fun resolveCached(resolver: (ArendReferenceElement) -> Referable?, ref : ArendReferenceElement) : Referable?
}

private fun getDefinitionOfLocalElement(element: PsiElement) =
    (element as? ArendCompositeElement)?.ancestorsUntilFile?.firstOrNull { it is ArendDefinition || it is ArendStatCmd || it is ArendDefModule } as? ArendDefinition

class ArendResolveCacheImpl(project: Project) : ArendResolveCache {
    private val globalMap: ConcurrentMap<ArendReferenceElement, GlobalReferable> = ContainerUtil.createConcurrentWeakKeySoftValueMap()
    private val localMap: ConcurrentMap<ArendDefinition, HashMap<ArendReferenceElement, Referable>> = ContainerUtil.createConcurrentWeakKeySoftValueMap()

    override fun resolveCached(resolver: (ArendReferenceElement) -> Referable?, ref: ArendReferenceElement): Referable? {
        val globalRef = globalMap[ref]
        if (globalRef != null) {
            return globalRef
        }

        val def = getDefinitionOfLocalElement(ref)
        val defMap = if (def is ArendDefinition) localMap.computeIfAbsent(def) { HashMap() } else null
        if (defMap != null) {
            val localRef = defMap[ref]
            if (localRef != null) {
                return localRef
            }
        }

        val result = resolver(ref)
        if (result != null) {
            if (result is GlobalReferable) {
                globalMap[ref] = result
            } else if (defMap != null) {
                defMap[ref] = result
            }
        }

        return result
    }

    init {
        PsiManager.getInstance(project).addPsiTreeChangeListener(ResolveCacheCleaner())
    }

    private inner class ResolveCacheCleaner : PsiTreeChangeAdapter() {
        override fun beforeChildReplacement(event: PsiTreeChangeEvent) = update(event.oldChild, event.newChild, event.parent)

        override fun beforeChildMovement(event: PsiTreeChangeEvent) = update(event.child, null, event.oldParent)

        override fun beforeChildRemoval(event: PsiTreeChangeEvent) = update(event.child, null, event.parent)

        override fun beforeChildAddition(event: PsiTreeChangeEvent) = update(event.child, null, event.parent)

        private fun update(oldChild: PsiElement?, newChild: PsiElement?, parent: PsiElement) {
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