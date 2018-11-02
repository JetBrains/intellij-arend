package org.arend.formatting.block

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.arend.psi.ArendElementTypes.ATOM_ARGUMENT
import org.arend.psi.ArendElementTypes.IMPLICIT_ARGUMENT
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
        var child = myNode.firstChildNode
        val blocks = ArrayList<Block>()
        while (child != null && child.elementType != ATOM_ARGUMENT) {
            blocks.add(createArendBlock(child, null, null, Indent.getNoneIndent()))
            child = child.treeNext
        }



        val expressionVisitor = object : BaseAbstractExpressionVisitor<Void, Concrete.Expression>(null) {
            override fun visitBinOpSequence(data: Any?, left: Abstract.Expression, sequence: Collection<Abstract.BinOpSequenceElem>, errorData: Abstract.ErrorData?, params: Void?) =
                    parseBinOp(left, sequence)
        }

        val cExpr = (node.psi as ArendExpr).accept(expressionVisitor, null)
        if (cExpr != null) return transform(cExpr).subBlocks

        return ArrayList()

        /*val n = (myNode.psi as ArendArgumentAppExpr).argumentList.size > 1
        val blocks2 = ArrayList<Block>()
        val alignment = Alignment.createAlignment()
        var flag = true
        while (child != null) {
            flag = if (child.elementType != WHITE_SPACE) {
                blocks2.add(createArendBlock(child, null, if (flag && n) alignment else null, Indent.getNoneIndent()))
                false
            } else child.text.contains('\n')
            child = child.treeNext

        }
        if (blocks2.isNotEmpty())
            blocks.add(object : AbstractArendBlock(myNode, null, null, Indent.getContinuationIndent()) {
                override fun buildChildren(): MutableList<Block> = blocks2
                override fun getTextRange(): TextRange {
                    val f = blocks2.first()
                    val l = blocks2.last()
                    return TextRange(f.textRange.startOffset, l.textRange.endOffset)
                }
            })*/

    }


    private fun transform(cExpr: Concrete.Expression): AbstractArendBlock {
        if (cExpr is Concrete.AppExpression) {
            val blocks = ArrayList<Block>()
            blocks.addAll(cExpr.arguments.asSequence().map { v: Concrete.Argument ->

                val vExprData = v.expression.data
                val blocks2 = ArrayList<Block>()
                var lb: PsiElement? = null
                var rb: PsiElement? = null
                if (vExprData is PsiElement) {
                    var vp: PsiElement? = vExprData
                    //Probably rewrite this
                    do {
                        vp = vp?.parent
                        if (vp is ArendImplicitArgument) {
                            lb = vp.lbrace
                            rb = vp.rbrace
                        }
                    } while (vp != null && vp.node != null && !(vp.node.elementType == ATOM_ARGUMENT || vp.node.elementType == IMPLICIT_ARGUMENT))

                    val te = transform(v.expression)
                    if (lb != null && !te.textRange.contains(lb.node.textRange))
                        blocks2.add(createArendBlock(lb.node, null, null, Indent.getNoneIndent()))
                    blocks2.add(te)
                    if (rb != null && !te.textRange.contains(rb.node.textRange))
                        blocks2.add(createArendBlock(rb.node, null, null, Indent.getNoneIndent()))
                } else throw IllegalStateException()
                SimplestBlock(myNode, blocks2, null, null, Indent.getNoneIndent())

            }.toList())
            val fData = cExpr.function.data
            if (fData is PsiElement) {
                val fBlock = createArendBlock(fData.node, null, null, Indent.getNoneIndent())
                var haveBlockWithThisRange = false
                for (b in blocks) if (b.textRange.contains(fBlock.textRange)) {
                    haveBlockWithThisRange = true
                    break;
                }
                if (!haveBlockWithThisRange) blocks.add(fBlock)
            }
            blocks.sortBy { it.textRange.startOffset }
            return SimplestBlock(myNode, blocks, null, null, Indent.getNoneIndent())
        } else {
            val myData = cExpr.data
            if (myData is PsiElement) {
                return createArendBlock(myData.node, null, null, Indent.getNoneIndent())
            }
            throw IllegalArgumentException()
        }
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