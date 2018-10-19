package org.arend.formatting.block

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.TokenType
import com.intellij.psi.formatter.common.AbstractBlock
import java.util.ArrayList

class ExpressionBlock(node: ASTNode, wrap: Wrap?, alignment: Alignment?, private val startingIndent: Int? = null, private val parentBlock: ExpressionBlock? = null): AbstractBlock(node, wrap, alignment) {

    /* override fun getIndent(): Indent? {
        val i = calculateIndent()
        if (i != null) {
            var j = 0
            var rootBlock = parentBlock
            while (rootBlock != null) {
                val pi = rootBlock.calculateIndent()
                if (pi != null) j += pi
                rootBlock = rootBlock.parentBlock
            }
            return Indent.getSpaceIndent(i - j, false)
        }
        return null
    }

    fun calculateIndent(): Int? {
        if (node.psi.prevSibling is PsiWhiteSpace && node.psi.prevSibling.textContains('\n') && parentBlock != null) {
            var rootBlock = parentBlock
            while (rootBlock?.parentBlock != null) rootBlock = rootBlock.parentBlock
            if (rootBlock != null) {
                val myAbsoluteIndent = SimpleArendBlock.getIndent(node.psi.prevSibling.text)
                val rootAbsoluteIndent = rootBlock.startingIndent
                if (myAbsoluteIndent != null && rootAbsoluteIndent != null && myAbsoluteIndent >= rootAbsoluteIndent) {
                    return myAbsoluteIndent - rootAbsoluteIndent
                }
            }
        }
        return null
    } */

    override fun isLeaf(): Boolean = myNode.firstChildNode == null

    override fun getSpacing(child1: Block?, child2: Block): Spacing? = null

    override fun buildChildren(): MutableList<Block> {
        val blocks = ArrayList<Block>()
        var child: ASTNode? = myNode.firstChildNode

        while (child != null) {
            if (child.elementType != TokenType.WHITE_SPACE) {
                val block = ExpressionBlock(child, null, null, null, this)
                blocks.add(block)
            }
            child = child.treeNext
        }
        return blocks

    }
}