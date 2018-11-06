package org.arend.formatting.block

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.formatter.common.AbstractBlock
import org.arend.psi.ArendArgumentAppExpr
import org.arend.psi.ArendDefFunction
import org.arend.psi.ArendElementTypes
import org.arend.psi.ArendFunctionBody

abstract class AbstractArendBlock(node: ASTNode, wrap: Wrap?, alignment: Alignment?, private val myIndent: Indent?) : AbstractBlock(node, wrap, alignment) {
    override fun getIndent(): Indent? = myIndent

    override fun isLeaf(): Boolean = myNode.firstChildNode == null

    override fun getSpacing(child1: Block?, child2: Block): Spacing? = null

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes =
            ChildAttributes(Indent.getNoneIndent(), null)

    fun isLBrace() = myNode.elementType == ArendElementTypes.LBRACE

    fun createArendBlock(childNode: ASTNode, childWrap: Wrap?, childAlignment: Alignment?, indent: Indent?): AbstractArendBlock {
        val childPsi = childNode.psi
        return if (childPsi is ArendArgumentAppExpr && childPsi.argumentList.isNotEmpty()) ArgumentAppExprBlock(childNode, childWrap, childAlignment, indent)
        else if (childPsi is ArendFunctionBody) FunctionBodyBlock(childNode, childWrap, childAlignment, indent)
        else if (childPsi is ArendDefFunction) DefFunctionBlock(childNode, childWrap, childAlignment, indent)
        else SimpleArendBlock(childNode, childWrap, childAlignment, indent)
    }


}