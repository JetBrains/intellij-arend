package org.arend

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.LBRACE
import org.arend.psi.ArendElementTypes.RBRACE

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
    ) : ArendVisitor() {

        override fun visitCaseExpr(o: ArendCaseExpr) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitFunctionBody(o: ArendFunctionBody) = fold(o)

        override fun visitDataBody(o: ArendDataBody) = fold(o)

        override fun visitCoClauses(o: ArendCoClauses) = fold(o)

        override fun visitConstructor(o: ArendConstructor) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitConstructorClause(o: ArendConstructorClause) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitDefClass(o: ArendDefClass) {
            foldBetween(o, o.lbrace, o.rbrace)
            foldBetween(o, o.fatArrow, null)
        }

        override fun visitNewExpr(o: ArendNewExpr) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitNewArg(o: ArendNewArg) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitWhere(o: ArendWhere) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitComment(comment: PsiComment) {
            if (comment.tokenType == ArendElementTypes.BLOCK_COMMENT) fold(comment)
        }

        private fun fold(element: PsiElement) {
            val textRange = element.textRange
            if (!textRange.isEmpty) {
                descriptors += FoldingDescriptor(element.node, textRange)
            }
        }

        private fun foldBetween(element: PsiElement, left: PsiElement?, right: PsiElement?) {
            if (left != null) {
                val range = TextRange(left.textOffset, right?.textOffset?.let { it + 1 } ?: element.textRange.endOffset)
                if (!range.isEmpty) {
                    descriptors += FoldingDescriptor(element.node, range)
                }
            }
        }
    }
}
