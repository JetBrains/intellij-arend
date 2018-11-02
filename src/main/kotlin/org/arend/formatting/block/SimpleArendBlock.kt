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
        val child = if (newChildIndex > 0) {
            subBlocks[newChildIndex-1].let {
                if (it is AbstractArendBlock && it.isLBrace())
                    subBlocks.getOrNull(newChildIndex) else it
            }
        } else null
        val indent = if (child == null) Indent.getNoneIndent() else child.indent
        return ChildAttributes(indent, child?.alignment)
    }

    override fun buildChildren(): MutableList<Block> {
        val blocks = ArrayList<Block>()
        var child: ASTNode? = myNode.firstChildNode
        val alignment = Alignment.createAlignment()
        val alignment2 = Alignment.createAlignment()
        val nodePsi = myNode.psi

        while (child != null) {
            if (child.elementType != WHITE_SPACE) {
                val childPsi = child.psi
                val indent: Indent? =
                if (child.elementType == CO_CLAUSE ||
                        child.elementType == STATEMENT ||
                        child.elementType == CONSTRUCTOR_CLAUSE ||
                        myNode.elementType == CO_CLAUSE && childPsi is ArendExpr ||
                        child.psi is ArendWhere ||
                        myNode.elementType == LET_EXPR && childPsi is ArendExpr ||
                        myNode.elementType == LET_CLAUSE && childPsi is ArendExpr ||
                        child.elementType == TUPLE_EXPR ||
                        nodePsi is ArendFunctionBody && nodePsi.coClauses == null && nodePsi.functionClauses == null ||
                        child.elementType == CLASS_STAT ||
                        childPsi is ArendExpr && myNode.elementType == LAM_EXPR)
                    Indent.getNormalIndent() else
                if (childPsi is ArendExpr && (myNode.elementType == PI_EXPR ||
                        myNode.elementType == SIGMA_EXPR))
                    Indent.getContinuationIndent() else
                    Indent.getNoneIndent()

                if ((myNode.elementType == FUNCTION_CLAUSES || myNode.elementType == LET_EXPR || myNode.elementType == DATA_BODY || myNode.elementType == CONSTRUCTOR) &&
                        child.elementType == PIPE) {
                    val clauseGroup = findClauseGroup(child)
                    if (clauseGroup != null) {
                        child = clauseGroup.first.treeNext
                        blocks.add(GroupBlock(myNode, clauseGroup.second, wrap, alignment, Indent.getNormalIndent()))
                        continue
                    }
                }

                val align = when {
                    myNode.elementType == LET_EXPR -> when {
                        child.elementType == LET_KW || child.elementType == IN_KW -> alignment2
                        child.elementType == LINE_COMMENT -> alignment
                        else -> null
                    }
                    child.elementType == CO_CLAUSE -> alignment
                    else -> null
                }


                val block = createArendBlock(child, null, align, indent)
                blocks.add(block)

            }
            child = child.treeNext
        }
        return blocks
    }

    override fun isIncomplete(): Boolean {
        val psi = myNode.psi
        if (psi is ArendNewExpr) {
            return psi.lbrace != null && psi.rbrace == null
        }
        return false
    }

    companion object {

        fun findClauseGroup(child: ASTNode): Pair<ASTNode, List<ASTNode>>? {
            var currChild: ASTNode? = child
            val groupNodes = ArrayList<ASTNode>()
            while (currChild != null) {
                groupNodes.add(currChild)
                if (currChild.elementType == CLAUSE || currChild.elementType == LET_CLAUSE || currChild.elementType == CONSTRUCTOR) {
                    return Pair(currChild, groupNodes)
                }
                currChild = currChild.treeNext
            }
            return null
        }
    }
}