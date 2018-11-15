package org.arend.formatting.block

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.TokenType
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.formatter.common.AbstractBlock
import org.arend.psi.*
import java.util.ArrayList

class RootBlock(node: ASTNode, val settings: CommonCodeStyleSettings?):
        AbstractBlock(node, null, null) {
    override fun isLeaf(): Boolean = false

     override fun getSpacing(child1: Block?, child2: Block): Spacing? {
         if (child1 is AbstractBlock && child2 is AbstractBlock) {
             val psi1 = child1.node.psi
             val psi2 = child2.node.psi
             if (psi1 is ArendStatement && psi2 is ArendStatement) {
                 val needLineFeed = psi1.statCmd == null || psi2.statCmd == null
                 val i = if (needLineFeed) 2 else 1
                 return Spacing.createSpacing(0, Integer.MAX_VALUE, i, false, i-1)
             }
         }
         return null
    }

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes =
            ChildAttributes.DELEGATE_TO_PREV_CHILD

    override fun getIndent(): Indent? = Indent.getNoneIndent()

    override fun buildChildren(): MutableList<Block> {
        val blocks = ArrayList<Block>()
        var child: ASTNode? = myNode.firstChildNode

        while (child != null) {
            if (child.elementType != TokenType.WHITE_SPACE) {
                val block = SimpleArendBlock(child, settings, null, null, Indent.getNoneIndent())
                blocks.add(block)
            }
            child = child.treeNext
        }
        return blocks
    }

    override fun isIncomplete(): Boolean = true
}