package org.arend.formatting.block

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.TokenType.WHITE_SPACE
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import java.util.ArrayList

class SimpleArendBlock(node: ASTNode, wrap: Wrap?, alignment: Alignment?, myIndent: Indent?): AbstractArendBlock(node, wrap, alignment, myIndent) {

    override fun getSpacing(child1: Block?, child2: Block): Spacing? {
        if (myNode.psi is ArendFunctionClauses) {
            val spacing = SpacingImpl(0, 0, 1, false, false, false, 1, false, 1)
            if (child2 is SimpleArendBlock && child2.node.elementType == PIPE)
                return spacing
        }
        return null
    }

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        val child = if (newChildIndex < subBlocks.size) subBlocks[newChildIndex] else null
        val indent = if (child == null) Indent.getNoneIndent() else child.indent
        return ChildAttributes(indent, null)
    }

    override fun buildChildren(): MutableList<Block> {
        val blocks = ArrayList<Block>()
        var child: ASTNode? = myNode.firstChildNode
        val alignment = Alignment.createAlignment()
        val alignment2 = Alignment.createAlignment()

        while (child != null) {
            if (child.elementType != WHITE_SPACE) {
                val childPsi = child.psi
                val indent: Indent? =
                if (child.elementType == CO_CLAUSE ||
                        child.elementType == STATEMENT ||
                        child.elementType == CONSTRUCTOR_CLAUSE ||
                        child.elementType == FUNCTION_BODY && childPsi is ArendFunctionBody && childPsi.expr != null ||
                        myNode.elementType == CO_CLAUSE && childPsi is ArendExpr ||
                        child.psi is ArendWhere ||
                        myNode.elementType == LET_EXPR && child.psi is ArendExpr)
                    Indent.getNormalIndent() else
                if (myNode.elementType == ARGUMENT_APP_EXPR ||
                        child.elementType == TUPLE_EXPR ||
                        childPsi is ArendExpr && (myNode.elementType == PI_EXPR ||
                                myNode.elementType == SIGMA_EXPR ||
                                myNode.elementType == LAM_EXPR) )
                    Indent.getContinuationIndent() else
                    Indent.getNoneIndent()

                val wrap = if (child.elementType == FUNCTION_BODY) Wrap.createWrap(WrapType.NORMAL, true) else null

                if ((myNode.elementType == FUNCTION_CLAUSES || myNode.elementType == LET_EXPR) && child.elementType == PIPE) {
                    val clauseGroup = findClauseGroup(child)
                    if (clauseGroup != null) {
                        child = clauseGroup.first.treeNext
                        blocks.add(GroupBlock(myNode, clauseGroup.second, wrap, alignment, Indent.getNormalIndent()))
                        continue
                    }
                }

                val align = if (myNode.elementType == LET_EXPR && (child.elementType == LET_KW || child.elementType == IN_KW))
                    alignment2 else null

                val block = SimpleArendBlock(child, wrap, align, indent)
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
                if (currChild.elementType == CLAUSE || currChild.elementType == LET_CLAUSE) {
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