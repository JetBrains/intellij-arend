package org.arend.psi.listener

import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.impl.ArendGroup


abstract class ArendPsiListener : PsiTreeChangeAdapter() {
    protected abstract fun updateDefinition(def: ArendDefinition)

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
        val child = event.child
        if (child is ArendFile) { // whole file has been removed
            invalidateChildren(child)
        } else {
            processParent(event, true)
        }
    }

    private fun isDynamicDef(elem: PsiElement?) = elem is ArendClassStat && (elem.definition != null || elem.defModule != null)

    private fun processParent(event: PsiTreeChangeEvent, checkCommentStart: Boolean) {
        if (event.file is ArendFile) {
            processChildren(event.child)
            processChildren(event.oldChild)
            processParent(event.child, event.oldChild, event.newChild, event.parent ?: event.oldParent, checkCommentStart)
        }
    }

    fun processParent(child: PsiElement?, oldChild: PsiElement?, newChild: PsiElement?, parent: PsiElement?, checkCommentStart: Boolean) {
        if (child is PsiErrorElement ||
            child is PsiWhiteSpace ||
            child is ArendWhere ||
            isDynamicDef(child) ||
            child is LeafPsiElement && AREND_COMMENTS.contains(child.node.elementType)) {
            return
        }
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

        ((parent as? ArendDefIdentifier)?.parent as? ArendGroup)?.let { invalidateChildren(it) }

        var elem = parent
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

    private fun invalidateChildren(group: ArendGroup) {
        if (group is ArendDefinition) {
            updateDefinition(group)
        }
        for (subgroup in group.subgroups) {
            invalidateChildren(subgroup)
        }
        for (subgroup in group.dynamicSubgroups) {
            invalidateChildren(subgroup)
        }
    }

    private fun processChildren(element: PsiElement?) {
        when (element) {
            is ArendGroup -> invalidateChildren(element)
            is ArendStatement -> {
                element.definition?.let { invalidateChildren(it) }
                element.defModule?.let { invalidateChildren(it) }
            }
        }
    }
}