package org.arend.formatting.block

import com.intellij.formatting.*
import com.intellij.psi.TokenType
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.arend.psi.ArendDefFunction
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.ArendExpr
import org.arend.psi.ArendFunctionBody
import java.util.ArrayList

class DefFunctionBlock(val defFunc: ArendDefFunction, settings: CommonCodeStyleSettings?, wrap: Wrap?, alignment: Alignment?, myIndent: Indent?) :
        AbstractArendBlock(defFunc.node, settings, wrap, alignment, myIndent) {
    override fun buildChildren(): MutableList<Block> {
        val result = ArrayList<Block>()
        var c = node.firstChildNode
        var first = true
        val alignment = Alignment.createAlignment()

        while (c != null) {
            if (c.elementType != TokenType.WHITE_SPACE) {
                val cPsi = c.psi
                val notFBodyWithClauses = if (cPsi is ArendFunctionBody) cPsi.fatArrow != null else true //Needed for correct indentation of fat arrow
                val indent = if (!first && notFBodyWithClauses) Indent.getNormalIndent() else Indent.getNoneIndent()
                val align = if (c.elementType == NAME_TELE || c.elementType == TYPE_TELE) alignment else null

                result.add(createArendBlock(c, null, align, indent))
            }
            c = c.treeNext
            first = false
        }

        return result
    }

    override fun getSpacing(child1: Block?, child2: Block): Spacing? {
        val spacingFA = SpacingImpl(1, 1, 0, false, true, false, 0, false, 0)
        val spacingColon = SpacingImpl(1, 1, 0, false, true, true, 0, false, 0)
        if (child2 is FunctionBodyBlock) {
            val child1node = (child1 as? AbstractArendBlock)?.node
            if (child1node != null && child1node.elementType != LINE_COMMENT && child2.functionBody.fatArrow != null) return spacingFA
        } else if (child1 is AbstractArendBlock && child2 is AbstractArendBlock) {
            val child1et = child1.node.elementType
            val child2psi = child2.node.psi
            if (child1et == COLON && child2psi is ArendExpr) return spacingColon
        }
        return null
    }

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        //printChildAttributesContext(newChildIndex)
        return if (newChildIndex == subBlocks.size && defFunc.functionBody != null)
                        ChildAttributes.DELEGATE_TO_PREV_CHILD
                        else ChildAttributes(Indent.getNormalIndent(), null)
    }
}