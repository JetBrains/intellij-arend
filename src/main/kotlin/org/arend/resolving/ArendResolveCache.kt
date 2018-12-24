package org.arend.resolving

import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.util.containers.ContainerUtil
import org.arend.naming.reference.Referable
import org.arend.psi.ArendElementTypes
import org.arend.psi.ext.ArendReferenceElement
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentMap

interface ArendResolveCache {
    fun resolveCached(resolver: (ArendReferenceElement) -> Referable?, ref : ArendReferenceElement) : Referable?
    fun clearCache()
}

class ArendResolveCacheImpl(project: Project): ArendResolveCache {
    private val map : ConcurrentMap<ArendReferenceElement, Referable> =
            ContainerUtil.createConcurrentWeakKeySoftValueMap(100, 0.75f,
                    Runtime.getRuntime().availableProcessors(), ContainerUtil.canonicalStrategy())

    override fun resolveCached(resolver: (ArendReferenceElement) -> Referable?, ref : ArendReferenceElement) : Referable? {
        var result = map[ref]

        if (result == null) {
            result = resolver(ref)
            if (result != null) {
                map[ref] = result
            }
        }

        return result
    }

    override fun clearCache() {
        map.clear()
    }

    init {
        PsiManager.getInstance(project).addPsiTreeChangeListener(ResolveCacheCleaner())
    }

    private inner class ResolveCacheCleaner : PsiTreeChangeAdapter() {
        override fun childReplaced(event: PsiTreeChangeEvent) = update(event)

        override fun childMoved(event: PsiTreeChangeEvent) = update(event)

        override fun childRemoved(event: PsiTreeChangeEvent) = update(event)

        override fun childAdded(event: PsiTreeChangeEvent) = update(event)

        private fun update(event: PsiTreeChangeEvent) {
            val oldChild = event.oldChild
            val newChild = event.newChild
            if (oldChild is PsiWhiteSpace && newChild is PsiWhiteSpace ||
                    oldChild is LeafPsiElement && isComment(oldChild.node.elementType) &&
                    newChild is LeafPsiElement && isComment(newChild.node.elementType)) {
                return
            }

            clearCache()
        }

        private fun isComment(element: IElementType) =
                element == ArendElementTypes.BLOCK_COMMENT || element == ArendElementTypes.LINE_COMMENT
    }
}