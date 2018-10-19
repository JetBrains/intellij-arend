package org.arend.formatting.block

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.formatter.common.AbstractBlock
import com.intellij.psi.TokenType.WHITE_SPACE
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.PIPE
import java.util.ArrayList

class SimpleArendBlock(node: ASTNode, wrap: Wrap?, alignment: Alignment?, private val myIndent: Indent?): AbstractBlock(node, wrap, alignment) {
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

    override fun buildChildren(): MutableList<Block> {
        val blocks = ArrayList<Block>()
        var child: ASTNode? = myNode.firstChildNode
        var myIndent: Int = 0

        while (child != null) {
            if (child.elementType != WHITE_SPACE) {
                var indent: Indent? = Indent.getNoneIndent()
                if (child.psi is ArendCoClause ||
                        ((myNode.psi is ArendFunctionClauses || myNode.psi is ArendConstructorClause) && child.elementType == PIPE) ||
                        child.psi is ArendWhere) indent = Indent.getNormalIndent()
                val block = if (child.psi is ArendExpr)
                    ExpressionBlock(child, null, null, myIndent) else
                    SimpleArendBlock(child, null, null, indent)
                blocks.add(block)
            } else {
                myIndent = getIndent(child.text) ?: 0
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