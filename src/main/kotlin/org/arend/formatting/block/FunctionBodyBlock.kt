package org.arend.formatting.block

import com.intellij.formatting.*
import com.intellij.psi.TokenType.ERROR_ELEMENT
import com.intellij.psi.TokenType.WHITE_SPACE
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import java.util.ArrayList

class FunctionBodyBlock(val functionBody: ArendFunctionBody, settings: CommonCodeStyleSettings?, wrap: Wrap?, alignment: Alignment?, myIndent: Indent?) :
        AbstractArendBlock(functionBody.node, settings, wrap, alignment, myIndent) {
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
        printChildAttributesContext(newChildIndex)

        if (newChildIndex > 0 && newChildIndex - 1 < subBlocks.size) {
            val prevBlock = subBlocks[newChildIndex - 1]
            val indent = if (prevBlock is AbstractArendBlock) {
                val eT = prevBlock.node.elementType
                when (eT) {
                    FAT_ARROW, COWITH_KW, ELIM, ERROR_ELEMENT -> Indent.getNormalIndent()
                    FUNCTION_CLAUSES, CO_CLAUSES -> return ChildAttributes.DELEGATE_TO_PREV_CHILD
                    else -> Indent.getNoneIndent()
                }
            } else Indent.getNoneIndent()
            return ChildAttributes(indent, null)
        }
        //return ChildAttributes(Indent.getNormalIndent(), null)
        return super.getChildAttributes(newChildIndex)
    }

    /*override fun isIncomplete(): Boolean {
        val eB = locateExpressionBlock()
        val ccB = locateCoClausesBlock()
        val fcB = locateFunctionClausesBlock()
        val result = when {
            functionBody.fatArrow != null -> eB == null || eB.isIncomplete
            functionBody.cowithKw != null -> ccB == null || ccB.isIncomplete
            else -> fcB == null || fcB.isIncomplete
        }
        System.out.println("FunctionBodyBlock.isIncomplete(${node.text}) = $result")
        return result
    }*/

    override fun getSpacing(child1: Block?, child2: Block): Spacing? {
        val spacingCRLF = SpacingImpl(1, 1, 0, false, true, true, 0, false, 1)
        if (child1 is AbstractArendBlock && child1.node.elementType == ArendElementTypes.FAT_ARROW) return spacingCRLF
        return super.getSpacing(child1, child2)
    }

    private fun locateExpressionBlock(): AbstractArendBlock? {
        for (b in subBlocks) if (b is AbstractArendBlock && b.node.psi is ArendExpr) return b
        return null
    }

    private fun locateCoClausesBlock(): AbstractArendBlock? {
        for (b in subBlocks) if (b is AbstractArendBlock && b.node.psi is ArendCoClauses) return b
        return null
    }

    private fun locateFunctionClausesBlock(): AbstractArendBlock? {
        for (b in subBlocks) if (b is AbstractArendBlock && b.node.psi is ArendFunctionClauses) return b
        return null
    }
}