package org.arend.formatting.block

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import java.util.ArrayList

class GroupBlock(myParentNode: ASTNode, private val myNodes: List<ASTNode>, private val myWrap: Wrap?, private val myAlignment: Alignment?, private val myIndent: Indent?):
        AbstractArendBlock(myParentNode, myWrap, myAlignment, myIndent) {
    override fun isLeaf(): Boolean = false

    override fun getSpacing(child1: Block?, child2: Block): Spacing? = null

    override fun getTextRange(): TextRange {
        if (myNodes.isNotEmpty()) {
            val f = myNodes.first()
            val l = myNodes.last()
            return TextRange(f.startOffset, l.textRange.endOffset)
        }
        return TextRange.EMPTY_RANGE
    }

    override fun buildChildren(): MutableList<Block> {
        val blocks = ArrayList<Block>()
        for (node in myNodes) {
            val block = SimpleArendBlock(node, null, null, Indent.getNoneIndent())
            blocks.add(block)
        }
        return blocks
    }

    override fun isIncomplete(): Boolean = false

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes = ChildAttributes(Indent.getNoneIndent(), null)

    override fun getIndent(): Indent? = myIndent
}