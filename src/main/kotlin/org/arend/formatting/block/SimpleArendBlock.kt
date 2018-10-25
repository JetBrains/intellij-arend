package org.arend.formatting.block

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.formatter.common.AbstractBlock
import com.intellij.psi.TokenType.WHITE_SPACE
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import java.util.ArrayList

class SimpleArendBlock(node: ASTNode, wrap: Wrap?, alignment: Alignment?, private val myIndent: Indent?): AbstractBlock(node, wrap, alignment) {
    private val pipeAlignment = Alignment.createAlignment()

    override fun isLeaf(): Boolean = myNode.firstChildNode == null

    override fun getIndent(): Indent? = myIndent

    override fun getSpacing(child1: Block?, child2: Block): Spacing? {
        if (myNode.psi is ArendFunctionClauses) {
            val spacing = SpacingImpl(0, 0, 1, false, false, false, 1, false, 1)
            if (child2 is SimpleArendBlock && child2.node.elementType == PIPE)
                return spacing
        }
        return null
    }

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        return ChildAttributes(if (myNode.elementType == CO_CLAUSES) Indent.getNormalIndent() else Indent.getNoneIndent(), pipeAlignment)
    }

    override fun buildChildren(): MutableList<Block> {
        val blocks = ArrayList<Block>()
        var child: ASTNode? = myNode.firstChildNode

        while (child != null) {
            if (child.elementType != WHITE_SPACE) {
                val indent: Indent? =
                if (child.psi is ArendCoClause ||
                    ((myNode.psi is ArendFunctionClauses || myNode.psi is ArendConstructorClause)) ||
                        child.psi is ArendStatement ||
                        (myNode.elementType == FUNCTION_BODY && child.psi is ArendExpr))
                    Indent.getNormalIndent() else
                    Indent.getNoneIndent()

                val wrap = if (child.elementType == FUNCTION_BODY) Wrap.createWrap(WrapType.NORMAL, true) else null

                val block = SimpleArendBlock(child, wrap, null, indent)
                blocks.add(block)
            }
            child = child.treeNext
        }
        return blocks
    }

    companion object {
        fun getIndent(str : String): Int? {
            var myStr = str
            if (myStr.indexOf('\n') == -1) return null
            while (myStr.indexOf('\n') != -1) myStr = myStr.substring(myStr.indexOf('\n')+1)
            return myStr.length
        }
    }
}