package org.arend.formatting.block

import com.intellij.formatting.*
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.arend.psi.ext.ArendCoClause
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.ext.ArendFunctionBody

open class GroupBlock(settings: CommonCodeStyleSettings?, private val blocks: MutableList<Block>, wrap: Wrap?, alignment: Alignment?, indent: Indent, parentBlock: AbstractArendBlock) :
        AbstractArendBlock(parentBlock.node, settings, wrap, alignment, indent, parentBlock) {
    override fun buildChildren(): MutableList<Block> = blocks

    override fun getTextRange(): TextRange {
        val f = blocks.first()
        val l = blocks.last()
        return TextRange(f.textRange.startOffset, l.textRange.endOffset)
    }

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        printChildAttributesContext(newChildIndex)
        when (node.elementType) {
            FUNCTION_CLAUSES, FUNCTION_BODY, INSTANCE_BODY -> return ChildAttributes.DELEGATE_TO_PREV_CHILD
        }
        return if (newChildIndex == blocks.size) ChildAttributes(indent, alignment) else super.getChildAttributes(newChildIndex)
    }

    override fun getSpacing(child1: Block?, child2: Block): Spacing? =
        if (node.psi is ArendFunctionBody && child1 is SimpleArendBlock && child2 is SimpleArendBlock && child1.node.elementType == PIPE && child2.node.psi is ArendCoClause) {
            SimpleArendBlock.oneSpaceNoWrap
        } else super.getSpacing(child1, child2)

    override fun toString(): String {
        var blockText = ""
        for (b in blocks) blockText += "$b; "
        return "$blockText $textRange"
    }

}