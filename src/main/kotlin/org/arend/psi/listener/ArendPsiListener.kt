package org.arend.psi.listener

import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.impl.ArendGroup


abstract class ArendPsiListener : PsiTreeChangeAdapter() {
    abstract fun updateDefinition(def: ArendDefinition, file: ArendFile, isExternalUpdate: Boolean)

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
            invalidateChildren(child, child)
        } else {
            processParent(event, true)
        }
    }

    private fun isDynamicDef(elem: PsiElement?) = elem is ArendClassStat && (elem.definition != null || elem.defModule != null)

    private fun processParent(event: PsiTreeChangeEvent, checkCommentStart: Boolean) {
        (event.file as? ArendFile)?.let {
            processChildren(event.child, it)
            processChildren(event.oldChild, it)
            processParent(it, event.child, event.oldChild, event.newChild, event.parent ?: event.oldParent, checkCommentStart)
        }
    }

    fun processParent(file: ArendFile, child: PsiElement?, oldChild: PsiElement?, newChild: PsiElement?, parent: PsiElement?, checkCommentStart: Boolean) {
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

        ((parent as? ArendDefIdentifier)?.parent as? ArendGroup)?.let { invalidateChildren(it, file) }

        var elem = parent
        while (elem != null) {
            if (elem is ArendWhere || elem is ArendFile || isDynamicDef(elem)) {
                return
            }
            if (elem is ArendDefinition) {
                updateDefinition(elem, file, false)
                return
            }
            elem = elem.parent
        }
    }

    private fun invalidateChildren(group: ArendGroup, file: ArendFile) {
        if (group is ArendDefinition) {
            updateDefinition(group, file, false)
        }
        for (subgroup in group.subgroups) {
            invalidateChildren(subgroup, file)
        }
        for (subgroup in group.dynamicSubgroups) {
            invalidateChildren(subgroup, file)
        }
    }

    private fun processChildren(element: PsiElement?, file: ArendFile) {
        when (element) {
            is ArendGroup -> invalidateChildren(element, file)
            is ArendStatement -> {
                element.definition?.let { invalidateChildren(it, file) }
                element.defModule?.let { invalidateChildren(it, file) }
            }
        }
    }
}