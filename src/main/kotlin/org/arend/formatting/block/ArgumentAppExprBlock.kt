package org.arend.formatting.block

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.ArendExpr
import org.arend.psi.ArendImplicitArgument
import org.arend.term.abs.Abstract
import org.arend.term.abs.BaseAbstractExpressionVisitor
import org.arend.term.concrete.Concrete
import org.arend.typing.parseBinOp
import java.lang.IllegalArgumentException
import java.util.ArrayList

class ArgumentAppExprBlock(node: ASTNode, wrap: Wrap?, alignment: Alignment?, myIndent: Indent?) : AbstractArendBlock(node, wrap, alignment, myIndent) {
    override fun buildChildren(): MutableList<Block> {
        val expressionVisitor = object : BaseAbstractExpressionVisitor<Void, Concrete.Expression>(null) {
            override fun visitBinOpSequence(data: Any?, left: Abstract.Expression, sequence: Collection<Abstract.BinOpSequenceElem>, errorData: Abstract.ErrorData?, params: Void?) =
                    parseBinOp(left, sequence)
        }

        val cExpr = (node.psi as ArendExpr).accept(expressionVisitor, null)
        if (cExpr != null) return transform2(cExpr, myNode.psi.children.toList(), Alignment.createAlignment(), Indent.getNoneIndent()).subBlocks

        return ArrayList()
    }

    private fun transform2(cExpr: Concrete.Expression, aaeBlocks: List<PsiElement>, align: Alignment?, indent: Indent): AbstractArendBlock {
        val cExprData = cExpr.data
        if (cExpr is Concrete.AppExpression) {
            val blocks = ArrayList<Block>()
            val fData = cExpr.function.data
            val newAlign = if(cExpr.arguments.size > 2) Alignment.createAlignment() else null
            val newIndent = if (cExpr.arguments.size > 2) Indent.getNormalIndent() else Indent.getNoneIndent()
            blocks.addAll(cExpr.arguments.asSequence().map { transform2(it.expression, aaeBlocks, newAlign, newIndent) }.toList())
            if (fData is PsiElement) {
                val fBlock = createArendBlock(fData.node, null, null, Indent.getNoneIndent())
                var haveBlockInThisRange = false
                for (b in blocks) if (b.textRange.contains(fBlock.textRange)) {
                    haveBlockInThisRange = true
                    break
                }
                if (!haveBlockInThisRange) blocks.add(fBlock)
            }
            blocks.sortBy { it.textRange.startOffset }
            return SimplestBlock(myNode, blocks, null, align, indent)
        } else if (cExprData is PsiElement) {
            var node: ASTNode? = null
            for (psi in aaeBlocks) if (psi.textRange.contains(cExprData.node.textRange)) {
                node = psi.node
                break
            }
            if (node == null) throw IllegalStateException()

            return createArendBlock(node, null, align, indent)
        } else throw IllegalStateException()
    }

    class SimplestBlock(myNode: ASTNode, private val blocks: List<Block>, wrap: Wrap?, alignment: Alignment?, indent: Indent) : AbstractArendBlock(myNode, wrap, alignment, indent) {
        override fun buildChildren(): MutableList<Block> {
            val result = ArrayList<Block>()
            result.addAll(blocks)
            return result
        }

        override fun getTextRange(): TextRange {
            val f = blocks.first()
            val l = blocks.last()
            return TextRange(f.textRange.startOffset, l.textRange.endOffset)
        }
    }

}