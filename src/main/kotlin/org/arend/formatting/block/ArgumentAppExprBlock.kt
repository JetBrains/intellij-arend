package org.arend.formatting.block

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.arend.psi.ArendExpr
import org.arend.term.abs.Abstract
import org.arend.term.abs.BaseAbstractExpressionVisitor
import org.arend.term.concrete.Concrete
import org.arend.typing.parseBinOp

class ArgumentAppExprBlock(node: ASTNode, settings: CommonCodeStyleSettings?, wrap: Wrap?, alignment: Alignment?, myIndent: Indent?, parentBlock: AbstractArendBlock?) :
        AbstractArendBlock(node, settings, wrap, alignment, myIndent, parentBlock) {
    override fun buildChildren(): MutableList<Block> {
        val expressionVisitor = object : BaseAbstractExpressionVisitor<Void, Concrete.Expression>(null) {
            override fun visitBinOpSequence(data: Any?, left: Abstract.Expression, sequence: Collection<Abstract.BinOpSequenceElem>, params: Void?) =
                parseBinOp(left, sequence)
        }

        val cExpr = runReadAction {
            (node.psi as ArendExpr).accept(expressionVisitor, null)
        }
        val children = myNode.getChildren(null).filter { it.elementType != TokenType.WHITE_SPACE }.toList()

        if (cExpr != null) return transform(cExpr, children, Alignment.createAlignment(), Indent.getNoneIndent()).subBlocks

        return ArrayList()
    }

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        printChildAttributesContext(newChildIndex)

        if (newChildIndex > 0) {
            val child = subBlocks[newChildIndex-1]

            val isLast = newChildIndex == subBlocks.size
            val alignNeeded = if (isLast) subBlocks.any { hasLfBefore(it) } else true

            val indent = if (child == null) Indent.getNoneIndent() else child.indent
            val align = if (alignNeeded) child?.alignment else getGrandParentAlignment()

            return ChildAttributes(indent, align)
        }

        return super.getChildAttributes(newChildIndex)
    }

    private fun getBounds(cExpr: Concrete.Expression, aaeBlocks: List<ASTNode>): TextRange {
        val cExprData = cExpr.data
        if (cExpr is Concrete.AppExpression) {
            val elements = ArrayList<TextRange>()
            val fData = cExpr.function.data

            elements.addAll(cExpr.arguments.asSequence().map { getBounds(it.expression, aaeBlocks) })

            if (fData is PsiElement) {
                val f = aaeBlocks.filter{ it.textRange.contains(fData.textRange) }
                if (f.size != 1) throw IllegalStateException()
                elements.add(f.first().textRange)
            }

            val startOffset = elements.asSequence().map { it.startOffset }.sorted().firstOrNull()
            val endOffset = elements.asSequence().map { it.endOffset }.sorted().lastOrNull()
            if (startOffset != null && endOffset != null) {
                return TextRange.create(startOffset, endOffset)
            }
        } else if (cExprData is PsiElement) {
            for (psi in aaeBlocks) if (psi.textRange.contains(cExprData.node.textRange)) {
                return psi.textRange
            }
        }
        throw IllegalStateException()
    }

    private fun transform(cExpr: Concrete.Expression, aaeBlocks: List<ASTNode>, align: Alignment?, indent: Indent): AbstractArendBlock {
        val cExprData = cExpr.data
        if (cExpr is Concrete.AppExpression) {
            val blocks = ArrayList<Block>()
            val fData = cExpr.function.data

            val sampleBlockList = ArrayList<Pair<Boolean, Int>>()
            sampleBlockList.addAll(cExpr.arguments.map {
                val data = it.expression.data
                Pair(false, (data as? PsiElement)?.node?.startOffset ?: -1) })
            if (fData is PsiElement) sampleBlockList.add(Pair(true, fData.node.startOffset))
            sampleBlockList.sortBy { it.second }

            val isPrefix = sampleBlockList.isNotEmpty() && sampleBlockList.first().first
            fun isFirst(argument: Concrete.Argument): Boolean =
                sampleBlockList.size > 0 && (argument.expression.data as? PsiElement)?.node?.startOffset ?: -1 == sampleBlockList[0].second

            val newAlign =  if (cExpr.arguments.size > 1) Alignment.createAlignment() else null
            val newIndent = if (isPrefix) Indent.getContinuationIndent() else Indent.getNoneIndent()

            blocks.addAll(cExpr.arguments.asSequence().map {
                val myBounds = getBounds(it.expression, aaeBlocks)
                val aaeBlocksFiltered = aaeBlocks.filter { aaeBlock -> myBounds.contains(aaeBlock.textRange)  }
                val first = isFirst(it)
                transform(it.expression, aaeBlocksFiltered,
                        if (!first) newAlign else null,
                        if (!first) newIndent else Indent.getNoneIndent()) })


            if (fData is PsiElement) {
                val f = aaeBlocks.filter{ it.textRange.contains(fData.node.textRange) }
                if (f.size != 1) throw java.lang.IllegalStateException()
                val fBlock = createArendBlock(f.first(), null, null, Indent.getNoneIndent())
                if (!blocks.any { it.textRange.contains(fBlock.textRange) }) blocks.add(fBlock)
            }

            blocks.sortBy { it.textRange.startOffset }

            // Dedicated search for "lost" blocks
            val lostBlocks = aaeBlocks.asSequence().sortedBy { it.startOffset }.toMutableList()
            for (block in blocks) {
                val toRemove = ArrayList<ASTNode>()
                for (aae in lostBlocks) {
                    if (block.textRange.contains(aae.textRange)) toRemove.add(aae)
                    if (aae.startOffset > block.textRange.endOffset) break
                }
                lostBlocks.removeAll(toRemove)
            }

            if (lostBlocks.isNotEmpty()) {
                for (lostBlock in lostBlocks) { // Remove BinOpParser blocks and replace them with containing lost blocks
                    val toRemove = ArrayList<Block>()
                    for (block in blocks) if (lostBlock.textRange.contains(block.textRange)) toRemove.add(block)
                    blocks.removeAll(toRemove)
                }


                for (lostBlock in lostBlocks) //Lost blocks that were not in BinOpParser output
                    blocks.add(createArendBlock(lostBlock, null, null, Indent.getNoneIndent()))
            }

            blocks.sortBy { it.textRange.startOffset }

            return GroupBlock(settings, blocks, null, align, indent, this)
        } else if (cExprData is PsiElement) {
            var psi: PsiElement? = null
            for (aaeBlock in aaeBlocks) if (aaeBlock.textRange.contains(cExprData.node.textRange)) {
                psi = aaeBlock.psi
                break
            }
            if (psi == null)
                throw IllegalStateException()

            val singletonSet = HashSet<PsiElement>()
            singletonSet.add(psi)

            return createArendBlock(psi.node, null, align, indent)
        } else throw IllegalStateException()
    }
}