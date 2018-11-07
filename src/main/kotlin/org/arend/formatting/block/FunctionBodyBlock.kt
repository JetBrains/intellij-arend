package org.arend.formatting.block

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.TokenType.ERROR_ELEMENT
import com.intellij.psi.TokenType.WHITE_SPACE
import org.arend.psi.ArendElementTypes
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.ArendExpr
import org.arend.psi.ArendFunctionBody
import java.util.ArrayList

class FunctionBodyBlock(node: ASTNode, wrap: Wrap?, alignment: Alignment?, myIndent: Indent?) : AbstractArendBlock(node, wrap, alignment, myIndent) {
    override fun buildChildren(): MutableList<Block> {
        val result = ArrayList<Block>()
        var c = node.firstChildNode

        while (c != null) {
            val indent = if (c.psi is ArendExpr || c.elementType == ArendElementTypes.LINE_COMMENT) Indent.getNormalIndent() else Indent.getNoneIndent()
            val wrap: Wrap? = if (c.psi is ArendExpr) Wrap.createWrap(WrapType.NORMAL, false) else null

            if (c.elementType != WHITE_SPACE) result.add(createArendBlock(c, wrap, null, indent))
            c = c.treeNext
        }

        return result
    }

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        if (newChildIndex > 0) {
            val prevBlock = subBlocks[newChildIndex-1]
            val indent =  if (prevBlock is AbstractArendBlock) {
                val eT = prevBlock.node.elementType
                when (eT) {
                    FAT_ARROW, COWITH_KW, ELIM, ERROR_ELEMENT -> Indent.getNormalIndent()
                    else -> Indent.getNoneIndent()
                }
            } else Indent.getNoneIndent()
            return ChildAttributes(indent, null)
        }
        return ChildAttributes(Indent.getNormalIndent(), null)
    }

    override fun getSpacing(child1: Block?, child2: Block): Spacing? {
        val spacingCRLF = SpacingImpl(1, 1, 0, false, false, true, 0, false, 1)
        if (child1 is AbstractArendBlock && child1.node.elementType == ArendElementTypes.FAT_ARROW) return spacingCRLF
        return super.getSpacing(child1, child2)
    }
}