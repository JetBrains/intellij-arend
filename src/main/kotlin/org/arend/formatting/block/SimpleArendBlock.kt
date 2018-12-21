package org.arend.formatting.block

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.TokenType
import com.intellij.psi.TokenType.ERROR_ELEMENT
import com.intellij.psi.TokenType.WHITE_SPACE
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.formatter.common.AbstractBlock
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import java.util.ArrayList

class SimpleArendBlock(node: ASTNode, settings: CommonCodeStyleSettings?, wrap: Wrap?, alignment: Alignment?, myIndent: Indent?) :
        AbstractArendBlock(node, settings, wrap, alignment, myIndent) {

    override fun getSpacing(child1: Block?, child2: Block): Spacing? {
        val oneCrlf = SpacingImpl(0, 0, 1, false, false, false, 1, false, 1)
        val oneBlankLine = Spacing.createSpacing(0, Integer.MAX_VALUE, 2, false, 1)
        val atMostOneCrlf = SpacingImpl(1, 1, 0, false, true, true, 0, false, 1)
        val spacingFA = SpacingImpl(1, 1, 0, false, true, false, 0, false, 0)
        val spacingColon = SpacingImpl(1, 1, 0, false, true, true, 0, false, 0)

        if (myNode.psi is ArendFunctionClauses) {
            if (child2 is SimpleArendBlock && child2.node.elementType == PIPE)
                return oneCrlf
        }

        if (myNode.psi is ArendFunctionBody) {
            if (child1 is AbstractArendBlock && child1.node.elementType == ArendElementTypes.FAT_ARROW) return atMostOneCrlf
            return super.getSpacing(child1, child2)
        }

        if (myNode.psi is ArendDefFunction) {
            if (child2 is AbstractArendBlock && child2.node.elementType == FUNCTION_BODY) {
                val child1node = (child1 as? AbstractArendBlock)?.node
                val child2node = (child2 as? AbstractArendBlock)?.node?.psi as? ArendFunctionBody
                if (child1node != null && child2node != null &&
                        child1node.elementType != LINE_COMMENT && child2node.fatArrow != null) return spacingFA
            } else if (child1 is AbstractArendBlock && child2 is AbstractArendBlock) {
                val child1et = child1.node.elementType
                val child2psi = child2.node.psi
                if (child1et == COLON && child2psi is ArendExpr) return spacingColon
            }
        }

        if (child1 is AbstractBlock && child2 is AbstractBlock) {
            val psi1 = child1.node.psi
            val psi2 = child2.node.psi
            val c1et = child1.node.elementType
            val c2et = child2.node.elementType
            if (psi1 is ArendStatement && psi2 is ArendStatement) {
                val needLineFeed = psi1.statCmd == null || psi2.statCmd == null
                val i = if (needLineFeed) 2 else 1
                return Spacing.createSpacing(0, Integer.MAX_VALUE, i, false, i - 1)
            } else if (psi1 is ArendStatement && c2et == RBRACE ||
                    c1et == LBRACE && psi2 is ArendStatement) return oneCrlf
            else {
                val isStatCmd1 = psi1 is ArendStatement && psi1.statCmd != null
                val isStatCmd2 = psi2 is ArendStatement && psi2.statCmd != null
                if (isStatCmd1 xor isStatCmd2) return oneBlankLine
            }
        }

        return null
    }

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        printChildAttributesContext(newChildIndex)

        val nodePsi = node.psi

        if (node.elementType == STATEMENT || nodePsi is ArendFile) return ChildAttributes.DELEGATE_TO_PREV_CHILD

        if (node.elementType == TUPLE && subBlocks.size > 1 && newChildIndex == 1)
            return ChildAttributes(Indent.getNormalIndent(), null)

        if (node.elementType == CO_CLAUSE && subBlocks.size == newChildIndex)
            return ChildAttributes(indent, alignment)

        val prevChild = if (newChildIndex > 0) subBlocks[newChildIndex - 1] else null
        val nextChild = if (newChildIndex < subBlocks.size) subBlocks[newChildIndex] else null

        if (prevChild is AbstractArendBlock) {
            val prevET = prevChild.node.elementType

            if (nodePsi is ArendWhere) {
                if (prevET == STATEMENT) return ChildAttributes.DELEGATE_TO_PREV_CHILD
                if (prevET == WHERE_KW || prevET == LBRACE || prevET == TokenType.ERROR_ELEMENT) return ChildAttributes(Indent.getNormalIndent(), null)
            }

            // Definitions
            if ((nodePsi is ArendDefinition || nodePsi is ArendClassField)
                    && newChildIndex <= subBlocks.size) when (prevET) {
                TYPE_TELE, NAME_TELE, FIELD_TELE -> {
                    val isLast = if (nextChild is AbstractArendBlock) when (nextChild.node.elementType) {
                        TYPE_TELE, NAME_TELE, FIELD_TELE -> false
                        else -> true
                    } else true
                    val align = if (!isLast || hasLfBefore(prevChild)) prevChild.alignment else null
                    return ChildAttributes(prevChild.indent, align)
                }
            }

            if ((nodePsi is ArendDefinition || nodePsi is ArendDefModule) && prevET == WHERE)
                return ChildAttributes.DELEGATE_TO_PREV_CHILD

            if (nodePsi is ArendDefClass) when (prevET) {
                DEF_IDENTIFIER, LONG_NAME -> return ChildAttributes(Indent.getNormalIndent(), null)
            }

            if (nodePsi is ArendDefData) when (prevET) {
                DEF_IDENTIFIER, UNIVERSE_EXPR, DATA_BODY, TYPE_TELE -> return ChildAttributes(Indent.getNormalIndent(), null)
            }

            if (nodePsi is ArendDefFunction) when (prevET) {
                FUNCTION_BODY -> return ChildAttributes.DELEGATE_TO_PREV_CHILD
            }

            if (nodePsi is ArendDefInstance) when (prevChild.node.psi) {
                is ArendExpr -> return ChildAttributes(Indent.getNormalIndent(), null)
                is ArendCoClauses -> return ChildAttributes.DELEGATE_TO_PREV_CHILD
                is ArendWhere -> return ChildAttributes(Indent.getNoneIndent(), null)
            }

            // Data and function bodies
            if (nodePsi is ArendDataBody && prevChild.node.psi is ArendElim)
                return ChildAttributes(Indent.getNormalIndent(), null)

            if (nodePsi is ArendFunctionBody) {
                val prevBlock = subBlocks[newChildIndex - 1]
                val indent = if (prevBlock is AbstractArendBlock) {
                    val eT = prevBlock.node.elementType
                    if (prevBlock.node.psi is ArendExpr) return ChildAttributes.DELEGATE_TO_PREV_CHILD
                    when (eT) {
                        FUNCTION_CLAUSES, CO_CLAUSES -> return ChildAttributes.DELEGATE_TO_PREV_CHILD
                        else -> Indent.getNormalIndent()
                    }
                } else Indent.getNoneIndent()
                return ChildAttributes(indent, null)
            }

            //Expressions

            when (node.elementType) {
                PI_EXPR, LAM_EXPR -> when (prevET) {
                    ERROR_ELEMENT -> if (newChildIndex > 1) {
                        val sB = subBlocks[newChildIndex - 2]
                        if (sB is AbstractBlock && sB.node.elementType == TYPE_TELE) return ChildAttributes(sB.indent, sB.alignment)
                    }
                    ARROW, FAT_ARROW, PI_KW, LAM_KW, TYPE_TELE, NAME_TELE -> {}
                    else -> return ChildAttributes.DELEGATE_TO_PREV_CHILD
                }
                ARR_EXPR -> return ChildAttributes.DELEGATE_TO_PREV_CHILD
                TUPLE -> when (prevET) {
                    RPAREN -> {}
                    else -> return ChildAttributes.DELEGATE_TO_PREV_CHILD
                }
                NEW_EXPR -> when (prevET) {
                    LBRACE -> {}
                    else -> return ChildAttributes.DELEGATE_TO_PREV_CHILD
                }
                TUPLE_EXPR -> return ChildAttributes.DELEGATE_TO_PREV_CHILD
            }

            //General case

            val indent = when (prevET) {
                LBRACE -> Indent.getNormalIndent()
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

        val nodePsi = myNode.psi
        val nodeET = myNode.elementType

        mainLoop@ while (child != null) {
            val childPsi = child.psi
            val childET = child.elementType

            if (childET != WHITE_SPACE) {
                val indent: Indent? =
                        if (childPsi is ArendExpr || childPsi is PsiErrorElement) when (nodeET) {
                            CO_CLAUSE, LET_EXPR, LET_CLAUSE, CLAUSE, FUNCTION_BODY, CLASS_IMPLEMENT -> Indent.getNormalIndent()
                            PI_EXPR, SIGMA_EXPR, LAM_EXPR -> Indent.getContinuationIndent()
                            else -> Indent.getNoneIndent()
                        } else if (childET == LINE_COMMENT || childET == BLOCK_COMMENT) when (nodeET) {
                            CO_CLAUSE, LET_EXPR, LET_CLAUSE, CLAUSE, FUNCTION_BODY,
                            FUNCTION_CLAUSES, CLASS_IMPLEMENT, DATA_BODY, CONSTRUCTOR, DEF_CLASS, CASE_EXPR,
                            DEF_FUNCTION, NEW_EXPR, WHERE, CO_CLAUSES, DEF_DATA -> Indent.getNormalIndent()
                            else -> Indent.getNoneIndent()
                        } else if (nodeET == DEF_FUNCTION) {
                            val notFBodyWithClauses = if (childPsi is ArendFunctionBody) childPsi.fatArrow != null else true
                            if ((blocks.size > 0) && notFBodyWithClauses) Indent.getNormalIndent() else Indent.getNoneIndent()
                        } else when (childET) {
                            CO_CLAUSE, CONSTRUCTOR_CLAUSE, WHERE, TUPLE_EXPR, CLASS_STAT,
                            NAME_TELE, TYPE_TELE, FIELD_TELE -> Indent.getNormalIndent()
                            STATEMENT -> if (nodePsi is ArendFile) Indent.getNoneIndent() else Indent.getNormalIndent()
                            else -> Indent.getNoneIndent()
                        }

                val wrap: Wrap? =
                        if (nodeET == FUNCTION_BODY && childPsi is ArendExpr) Wrap.createWrap(WrapType.NORMAL, false) else null

                val align = when (myNode.elementType) {
                    LET_EXPR -> when (childET) {
                        LET_KW, IN_KW -> alignment2
                        LINE_COMMENT -> alignment
                        else -> null
                    }
                    else -> when (childET) {
                        CO_CLAUSE, CLASS_STAT -> alignment
                        NAME_TELE, TYPE_TELE, FIELD_TELE -> alignment2
                        else -> null
                    }
                }

                if (childET == PIPE) when (nodeET) {
                    FUNCTION_CLAUSES, LET_EXPR, DATA_BODY, CONSTRUCTOR, DEF_CLASS, CASE_EXPR -> {
                        val clauseGroup = findClauseGroup(child, null)
                        if (clauseGroup != null) {
                            child = clauseGroup.first.treeNext
                            blocks.add(GroupBlock(myNode, settings, clauseGroup.second, null, alignment, Indent.getNormalIndent()))
                            continue@mainLoop
                        }
                    }
                }

                blocks.add(createArendBlock(child, wrap, align, indent))
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
                CLAUSE, LET_CLAUSE, CONSTRUCTOR, CLASS_FIELD, CLASS_FIELD_SYN, CLASS_IMPLEMENT -> return Pair(currChild, groupNodes)
            }
            currChild = currChild.treeNext
        }
        return null
    }
}