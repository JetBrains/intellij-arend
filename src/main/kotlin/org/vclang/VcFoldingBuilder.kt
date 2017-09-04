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
import org.vclang.psi.VcArgument
import org.vclang.psi.VcAtomPattern
import org.vclang.psi.VcCaseExpr
import org.vclang.psi.VcClassStats
import org.vclang.psi.VcClauses
import org.vclang.psi.VcConstructor
import org.vclang.psi.VcConstructorClause
import org.vclang.psi.VcDefClassView
import org.vclang.psi.VcElementTypes
import org.vclang.psi.VcElementTypes.LBRACE
import org.vclang.psi.VcElementTypes.RBRACE
import org.vclang.psi.VcFile
import org.vclang.psi.VcImplementStatements
import org.vclang.psi.VcTele
import org.vclang.psi.VcVisitor
import org.vclang.psi.VcWhere

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

        override fun visitConstructorClause(o: VcConstructorClause) =
            foldBetween(o, o.lbrace, o.rbrace)

        override fun visitClassStats(o: VcClassStats) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitDefClassView(o: VcDefClassView) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitImplementStatements(o: VcImplementStatements) = fold(o)

        override fun visitTele(o: VcTele) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitWhere(o: VcWhere) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitComment(comment: PsiComment) {
            if (comment.tokenType == VcElementTypes.BLOCK_COMMENT) fold(comment)
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
