package org.arend.formatting.block

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.formatter.common.AbstractBlock
import com.intellij.psi.TokenType.WHITE_SPACE
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import java.util.ArrayList

class SimpleArendBlock(node: ASTNode, wrap: Wrap?, alignment: Alignment?, myIndent: Indent?): AbstractArendBlock(node, wrap, alignment, myIndent) {
    private val pipeAlignment = Alignment.createAlignment()

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
                if (child.psi is ArendCoClause ||child.psi is ArendStatement ||
                        (myNode.elementType == FUNCTION_BODY && child.psi is ArendExpr))
                    Indent.getNormalIndent() else
                    if (child.elementType == ATOM_ARGUMENT) Indent.getContinuationIndent() else
                    Indent.getNoneIndent()

                val wrap = if (child.elementType == FUNCTION_BODY) Wrap.createWrap(WrapType.NORMAL, true) else null

                if (child.elementType == PIPE && myNode.elementType == FUNCTION_CLAUSES) {
                    val clauseGroup = findClauseGroup(child)
                    if (clauseGroup != null) {
                        child = clauseGroup.first.treeNext
                        blocks.add(GroupBlock(myNode, clauseGroup.second, wrap, null, Indent.getNormalIndent()))
                        continue
                    }
                }

                val block = SimpleArendBlock(child, wrap, null, indent)
                blocks.add(block)

            }
            child = child.treeNext
        }
        return blocks
    }

    companion object {

        fun findClauseGroup(child: ASTNode): Pair<ASTNode, List<ASTNode>>? {
            var currChild: ASTNode? = child
            val groupNodes = ArrayList<ASTNode>()
            while (currChild != null) {
                groupNodes.add(currChild)
                if (currChild.elementType == CLAUSE) {
                    return Pair(currChild, groupNodes)
                }
                currChild = currChild.treeNext
            }
            return null
        }

        fun getIndent(str : String): Int? {
            var myStr = str
            if (myStr.indexOf('\n') == -1) return null
            while (myStr.indexOf('\n') != -1) myStr = myStr.substring(myStr.indexOf('\n')+1)
            return myStr.length
        }
    }
}