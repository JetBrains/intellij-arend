package org.arend.formatting.block

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.TokenType.WHITE_SPACE
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import java.util.ArrayList

class SimpleArendBlock(node: ASTNode, wrap: Wrap?, alignment: Alignment?, myIndent: Indent?) : AbstractArendBlock(node, wrap, alignment, myIndent) {

    override fun getSpacing(child1: Block?, child2: Block): Spacing? {
        if (myNode.psi is ArendFunctionClauses) {
            val spacing = SpacingImpl(0, 0, 1, false, false, false, 1, false, 1)
            if (child2 is SimpleArendBlock && child2.node.elementType == PIPE)
                return spacing
        }
        return null
    }

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        System.out.println("SimpleArendBlock(${node.elementType}).getChildAttributes($newChildIndex)")
        val nodePsi = node.psi
        if (node.elementType == TUPLE && subBlocks.size > 1 && newChildIndex == 1 ||
                node.elementType == WHERE && newChildIndex >= 1 ||
                nodePsi is ArendDefInstance && newChildIndex == subBlocks.size-1 && nodePsi.where == null) {
            return ChildAttributes(Indent.getNormalIndent(), null)
        } else if (node.elementType == CO_CLAUSE && subBlocks.size == newChildIndex)
            return ChildAttributes(indent, alignment)
        else if (nodePsi is ArendDefInstance && newChildIndex == subBlocks.size && nodePsi.where != null)
            return ChildAttributes(Indent.getNoneIndent(), null)
        val child = if (newChildIndex > 0) {
            subBlocks[newChildIndex - 1].let {
                if (it is AbstractArendBlock && it.isLBrace())
                    subBlocks.getOrNull(newChildIndex) else it
            }
        } else null
        val indent = if (child is AbstractArendBlock && child.node.elementType == RBRACE) Indent.getNormalIndent() else
                     if (child == null) Indent.getNoneIndent() else child.indent
        return ChildAttributes(indent, child?.alignment)
    }

    override fun buildChildren(): MutableList<Block> {
        val blocks = ArrayList<Block>()
        var child: ASTNode? = myNode.firstChildNode
        val alignment = Alignment.createAlignment()
        val alignment2 = Alignment.createAlignment()
        val nodeET = myNode.elementType

        mainLoop@ while (child != null) {
            if (child.elementType != WHITE_SPACE) {
                val childPsi = child.psi

                val indent: Indent? = if (childPsi is ArendExpr || childPsi is PsiErrorElement) when (nodeET) {
                    CO_CLAUSE, LET_EXPR, LET_CLAUSE, CLAUSE -> Indent.getNormalIndent()
                    PI_EXPR, SIGMA_EXPR, LAM_EXPR -> Indent.getContinuationIndent()
                    else -> Indent.getNoneIndent()
                } else when (child.elementType) {
                    CO_CLAUSE, STATEMENT, CONSTRUCTOR_CLAUSE, WHERE, TUPLE_EXPR, CLASS_STAT -> Indent.getNormalIndent()
                    else -> Indent.getNoneIndent()
                }

                val align = when (myNode.elementType) {
                    LET_EXPR -> when (child.elementType) {
                        LET_KW, IN_KW -> alignment2
                        LINE_COMMENT -> alignment
                        else -> null
                    }
                    else -> when (child.elementType) {
                        CO_CLAUSE -> alignment
                        else -> null
                    }
                }

                if (child.elementType == PIPE) when (nodeET) {
                    FUNCTION_CLAUSES, LET_EXPR, DATA_BODY, CONSTRUCTOR, DEF_CLASS, CASE_EXPR -> {
                        val clauseGroup = findClauseGroup(child, null)
                        if (clauseGroup != null) {
                            child = clauseGroup.first.treeNext
                            blocks.add(GroupBlock(myNode, clauseGroup.second, null, alignment, Indent.getNormalIndent()))
                            continue@mainLoop
                        }
                    }
                }

                blocks.add(createArendBlock(child, null, align, indent))
            }
            child = child.treeNext

        }
        return blocks
    }

    override fun isIncomplete(): Boolean {
        val psi = myNode.psi
        val result =
                if (psi is ArendNewExpr) psi.lbrace != null && psi.rbrace == null
                else if (psi is ArendStatement && subBlocks.size == 1) subBlocks.first().isIncomplete
                else if (psi is ArendFunctionClauses) psi.rbrace == null
                else if (psi is ArendCoClauses) psi.rbrace == null
                else if (psi is ArendCoClause) true
                else if (psi is ArendDefInstance)
                    subBlocks.asSequence().filter { it is AbstractArendBlock && it.node.elementType == CO_CLAUSES }.firstOrNull().let { it?.isIncomplete ?: super.isIncomplete() }
                else super.isIncomplete()
        System.out.println("SimpleArendBlock.isIncomplete(${node.elementType}/${node.text}) = $result")
        return result
    }

    private fun findClauseGroup(child: ASTNode, childAlignment: Alignment?): Pair<ASTNode, List<Block>>? {
        var currChild: ASTNode? = child
        val groupNodes = ArrayList<Block>()
        while (currChild != null) {
            if (currChild.elementType != WHITE_SPACE) groupNodes.add(createArendBlock(currChild, null, childAlignment, Indent.getNoneIndent()))
            when (currChild.elementType) {
                CLAUSE, LET_CLAUSE, CONSTRUCTOR, CLASS_FIELD -> return Pair(currChild, groupNodes)
            }
            currChild = currChild.treeNext
        }
        return null
    }
}