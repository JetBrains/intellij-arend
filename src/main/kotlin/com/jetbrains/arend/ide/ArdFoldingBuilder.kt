package com.jetbrains.arend.ide

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.arend.ide.psi.*
import com.jetbrains.arend.ide.psi.ArdElementTypes.LBRACE
import com.jetbrains.arend.ide.psi.ArdElementTypes.RBRACE

class ArdFoldingBuilder : FoldingBuilderEx(), DumbAware {

    override fun buildFoldRegions(
            root: PsiElement,
            document: Document,
            quick: Boolean
    ): Array<out FoldingDescriptor> {
        if (root !is ArdFile) return emptyArray()
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
    ) : ArdVisitor() {

        override fun visitCaseExpr(o: ArdCaseExpr) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitFunctionBody(o: ArdFunctionBody) = fold(o)

        override fun visitDataBody(o: ArdDataBody) = fold(o)

        override fun visitCoClauses(o: ArdCoClauses) = fold(o)

        override fun visitConstructor(o: ArdConstructor) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitConstructorClause(o: ArdConstructorClause) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitDefClass(o: ArdDefClass) {
            foldBetween(o, o.lbrace, o.rbrace)
            foldBetween(o, o.fatArrow, null)
        }

        override fun visitNewExpr(o: ArdNewExpr) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitNewArg(o: ArdNewArg) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitWhere(o: ArdWhere) = foldBetween(o, o.lbrace, o.rbrace)

        override fun visitComment(comment: PsiComment) {
            if (comment.tokenType == ArdElementTypes.BLOCK_COMMENT) fold(comment)
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
