package org.arend.formatting.block

import com.intellij.formatting.*
import com.intellij.openapi.util.TextRange
import java.util.ArrayList

class CommentPieceBlock(private val myTextRange: TextRange, private val myAlign: Alignment?, private val myIndent: Indent?, val isDash: Boolean): Block {
    override fun getAlignment(): Alignment? = myAlign

    override fun isIncomplete(): Boolean = false

    override fun isLeaf(): Boolean = true

    override fun getSpacing(child1: Block?, child2: Block): Spacing? = null

    override fun getTextRange(): TextRange = myTextRange

    override fun getSubBlocks(): MutableList<Block> = ArrayList()

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes = ChildAttributes(Indent.getNoneIndent(), null)

    override fun getWrap(): Wrap? = null

    override fun getIndent(): Indent? = myIndent
}