package org.arend.formatting.block

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.TokenType.WHITE_SPACE
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import java.util.ArrayList

class SimpleArendBlock(node: ASTNode, settings: CommonCodeStyleSettings?, wrap: Wrap?, alignment: Alignment?, myIndent: Indent?) :
        AbstractArendBlock(node, settings, wrap, alignment, myIndent) {

    override fun getSpacing(child1: Block?, child2: Block): Spacing? {
        if (myNode.psi is ArendFunctionClauses) {
            val spacing = SpacingImpl(0, 0, 1, false, false, false, 1, false, 1)
            if (child2 is SimpleArendBlock && child2.node.elementType == PIPE)
                return spacing
        }
        return null
    }

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        printChildAttributesContext(newChildIndex)

        val nodePsi = node.psi

        if (node.elementType == STATEMENT) return ChildAttributes.DELEGATE_TO_PREV_CHILD

        if (node.elementType == TUPLE && subBlocks.size > 1 && newChildIndex == 1 ||
                node.elementType == WHERE && newChildIndex >= 1)
            return ChildAttributes(Indent.getNormalIndent(), null)

        if (node.elementType == CO_CLAUSE && subBlocks.size == newChildIndex)
            return ChildAttributes(indent, alignment)

        val prevChild = if (newChildIndex > 0 && newChildIndex - 1 < subBlocks.size) {
            subBlocks[newChildIndex - 1].let {
                if (it is AbstractArendBlock && it.isLBrace())
                    subBlocks.getOrNull(newChildIndex) else it
            }
        } else null

        if (prevChild is AbstractArendBlock) {
            val prevET = prevChild.node.elementType
            if (nodePsi is ArendDefClass) when (prevET) {
                DEF_IDENTIFIER, LONG_NAME -> return ChildAttributes(Indent.getNormalIndent(), null)
                WHERE -> return ChildAttributes(Indent.getNoneIndent(), null)
            }

            if (nodePsi is ArendDefData) when (prevET) {
                DEF_IDENTIFIER, UNIVERSE_EXPR, DATA_BODY -> return ChildAttributes(Indent.getNormalIndent(), null)
                WHERE -> return ChildAttributes(Indent.getNoneIndent(), null)
            }

            if (nodePsi is ArendDefInstance) when (prevChild.node.psi) {
                is ArendExpr -> return ChildAttributes(Indent.getNormalIndent(), null)
                is ArendCoClauses -> return ChildAttributes.DELEGATE_TO_PREV_CHILD
                is ArendWhere -> return ChildAttributes(Indent.getNoneIndent(), null)
            }

            if (nodePsi is ArendDataBody && prevChild.node.psi is ArendElim)
                return ChildAttributes(Indent.getNormalIndent(), null)

            val indent = when (prevET) {
                RBRACE -> Indent.getNormalIndent()
                else -> prevChild.indent
            }

            return ChildAttributes(indent, prevChild.alignment)
        }

        return super.getChildAttributes(newChildIndex)
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
                        NAME_TELE, TYPE_TELE -> alignment2
                        else -> null
                    }
                }

                if (child.elementType == PIPE) when (nodeET) {
                    FUNCTION_CLAUSES, LET_EXPR, DATA_BODY, CONSTRUCTOR, DEF_CLASS, CASE_EXPR -> {
                        val clauseGroup = findClauseGroup(child, null)
                        if (clauseGroup != null) {
                            child = clauseGroup.first.treeNext
                            blocks.add(GroupBlock(myNode, settings, clauseGroup.second, null, alignment, Indent.getNormalIndent()))
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

    private fun findClauseGroup(child: ASTNode, childAlignment: Alignment?): Pair<ASTNode, MutableList<Block>>? {
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