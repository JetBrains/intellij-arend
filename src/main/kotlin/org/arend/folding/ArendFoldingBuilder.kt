package org.arend.folding

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.LBRACE
import org.arend.psi.ArendElementTypes.RBRACE
import org.arend.psi.ext.*

class ArendFoldingBuilder : FoldingBuilderEx(), DumbAware {

    override fun buildFoldRegions(
            root: PsiElement,
            document: Document,
            quick: Boolean
    ): Array<out FoldingDescriptor> {
        if (root !is ArendFile) return emptyArray()
        val descriptors = mutableListOf<FoldingDescriptor>()
        val visitor = FoldingVisitor(descriptors)
        PsiTreeUtil.processElements(root) { it.accept(visitor); true }
        return descriptors.toTypedArray()
    }

    override fun getPlaceholderText(node: ASTNode): String = when {
        node.psi is PsiComment -> "{-...-}"
        node.elementType == LBRACE -> "{"
        node.elementType == RBRACE -> "}"
        else -> "{...}"
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false

    private class FoldingVisitor(
            private val descriptors: MutableList<FoldingDescriptor>
    ) : PsiElementVisitor() {

        override fun visitElement(element: PsiElement) {
            super.visitElement(element)
            when (element) {
                is ArendWithBody, is ArendConstructor, is ArendConstructorClause, is ArendDefClass, is ArendNewExpr, is ArendWhere -> foldBraces(element)
                is ArendFunctionBody, is ArendDataBody -> fold(element)
            }
        }

        override fun visitComment(comment: PsiComment) {
            if (comment.tokenType == ArendElementTypes.BLOCK_COMMENT) fold(comment)
        }

        private fun fold(element: PsiElement) {
            val textRange = element.textRange
            if (!textRange.isEmpty) {
                descriptors += FoldingDescriptor(element.node, textRange)
            }
        }

        private fun foldBraces(element: PsiElement) {
            val left = element.childOfType(LBRACE)
            if (left != null) {
                val range = TextRange(left.textOffset, element.childOfType(RBRACE)?.textOffset?.let { it + 1 } ?: element.textRange.endOffset)
                if (!range.isEmpty) {
                    descriptors += FoldingDescriptor(element.node, range)
                }
            }
        }
    }
}
