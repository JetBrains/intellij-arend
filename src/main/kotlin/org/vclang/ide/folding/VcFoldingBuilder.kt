package org.vclang.ide.folding


import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.vclang.lang.core.psi.*
import org.vclang.lang.core.psi.VcTypes.LBRACE
import org.vclang.lang.core.psi.VcTypes.RBRACE

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

        override fun visitArgument(o: VcArgument) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitAtomPattern(o: VcAtomPattern) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitCaseExpr(o: VcCaseExpr) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitClauses(o: VcClauses) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitConstructor(o: VcConstructor) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitConstructorClause(o: VcConstructorClause) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitDefClass(o: VcDefClass) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitDefClassView(o: VcDefClassView) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitImplementStatements(o: VcImplementStatements) = fold(o)

        override fun visitTele(o: VcTele) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitWhere(o: VcWhere) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitComment(comment: PsiComment) {
            if (comment.tokenType == VcTypes.BLOCK_COMMENT) fold(comment)
        }

        private fun fold(element: PsiElement) {
            descriptors += FoldingDescriptor(element.node, element.textRange)
        }

        private fun foldBetween(element: PsiElement, left: PsiElement?, right: PsiElement?) {
            if (left != null && right != null) {
                val range = TextRange(left.textOffset, right.textOffset + 1)
                descriptors += FoldingDescriptor(element.node, range)
            }
        }
    }
}
