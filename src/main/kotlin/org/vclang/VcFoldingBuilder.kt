package org.vclang

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.vclang.psi.*
import org.vclang.psi.VcElementTypes.LBRACE
import org.vclang.psi.VcElementTypes.RBRACE

class VcFoldingBuilder : FoldingBuilderEx(), DumbAware {

    override fun buildFoldRegions(
            root: PsiElement,
            document: Document,
            quick: Boolean
    ): Array<out FoldingDescriptor> {
        if (root !is VcFile) return emptyArray()
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
    ) : VcVisitor() {

        override fun visitCaseExpr(o: VcCaseExpr) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitFunctionBody(o: VcFunctionBody) = fold(o)

        override fun visitDataBody(o: VcDataBody) = fold(o)

        override fun visitCoClauses(o: VcCoClauses) = fold(o)

        override fun visitConstructor(o: VcConstructor) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitConstructorClause(o: VcConstructorClause) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitDefClass(o: VcDefClass) {
            foldBetween(o, o.lbrace, o.rbrace)
            foldBetween(o, o.fatArrow, null)
        }

        override fun visitNewExpr(o: VcNewExpr) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitNewArg(o: VcNewArg) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitWhere(o: VcWhere) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitComment(comment: PsiComment) {
            if (comment.tokenType == VcElementTypes.BLOCK_COMMENT) fold(comment)
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
