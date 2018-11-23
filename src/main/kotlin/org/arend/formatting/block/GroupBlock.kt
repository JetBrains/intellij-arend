package org.arend.formatting.block

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.CommonCodeStyleSettings

class GroupBlock(myEnclosingNode: ASTNode, settings: CommonCodeStyleSettings?, private val blocks: MutableList<Block>, wrap: Wrap?, alignment: Alignment?, indent: Indent) :
        AbstractArendBlock(myEnclosingNode, settings, wrap, alignment, indent) {
    override fun buildChildren(): MutableList<Block> = blocks

    override fun getTextRange(): TextRange {
        val f = blocks.first()
        val l = blocks.last()
        return TextRange(f.textRange.startOffset, l.textRange.endOffset)
    }

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        printChildAttributesContext(newChildIndex)
        return if (newChildIndex == blocks.size) ChildAttributes(indent, alignment) else super.getChildAttributes(newChildIndex)
    }

    override fun toString(): String {
        var blockText = ""
        for (b in blocks) blockText += b.toString()+"; "
        return "$blockText $textRange"
    }

}