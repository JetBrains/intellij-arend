package org.arend.formatting.block

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.TokenType
import org.arend.psi.ArendDefFunction
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.ArendExpr
import org.arend.psi.ArendFunctionBody
import java.util.ArrayList

class DefFunctionBlock(node: ASTNode, wrap: Wrap?, alignment: Alignment?, myIndent: Indent?) : AbstractArendBlock(node, wrap, alignment, myIndent) {
    override fun buildChildren(): MutableList<Block> {
        val result = ArrayList<Block>()
        var c = node.firstChildNode
        var first = true
        while (c != null) {
            if (c.elementType != TokenType.WHITE_SPACE) {
                val indent = if (c.elementType != FUNCTION_BODY && !first) Indent.getNormalIndent() else Indent.getNoneIndent()
                result.add(createArendBlock(c, null, null, indent))
            }
            c = c.treeNext
            first = false
        }

        return result
    }

    override fun getSpacing(child1: Block?, child2: Block): Spacing? {
        val spacingFA = SpacingImpl(1, 1, 0, false, false, false, 0, false, 0)
        val spacingColon = SpacingImpl(1, 1, 0, false, false, true, 0, false, 0)
        if (child2 is FunctionBodyBlock) {
            val childPsi = child2.node.psi
            if (childPsi is ArendFunctionBody && childPsi.fatArrow != null) return spacingFA
        } else if (child1 is AbstractArendBlock && child2 is AbstractArendBlock) {
            val child1et = child1.node.elementType
            val child2psi = child2.node.psi
            if (child1et == COLON && child2psi is ArendExpr) return spacingColon
        }
        return null
    }

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        return ChildAttributes(Indent.getNormalIndent(), null)
    }
}