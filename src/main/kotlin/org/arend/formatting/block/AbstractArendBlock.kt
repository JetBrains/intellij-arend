package org.arend.formatting.block

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.formatter.common.AbstractBlock
import org.arend.psi.ext.ArendArgumentAppExpr

abstract class AbstractArendBlock(node: ASTNode, val settings: CommonCodeStyleSettings?, wrap: Wrap?, alignment: Alignment?, private val myIndent: Indent?,
                                  private val parentBlock: AbstractArendBlock?) : AbstractBlock(node, wrap, alignment) {
    override fun getIndent(): Indent? = myIndent

    override fun isLeaf(): Boolean = myNode.firstChildNode == null

    override fun getSpacing(child1: Block?, child2: Block): Spacing? = null

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes =
            ChildAttributes(Indent.getNoneIndent(), null)

    fun createArendBlock(childNode: ASTNode, childWrap: Wrap?, childAlignment: Alignment?, indent: Indent?): AbstractArendBlock {
        val childPsi = childNode.psi
        return if (childPsi is ArendArgumentAppExpr && childPsi.argumentList.isNotEmpty())
            ArgumentAppExprBlock(childNode, settings, childWrap, childAlignment, indent, this)
        else SimpleArendBlock(childNode, settings, childWrap, childAlignment, indent, this)
    }


    fun getGrandParentAlignment(): Alignment? {
        if (parentBlock is AbstractArendBlock) {
            val gpBlock = parentBlock.parentBlock
            if (gpBlock is Block) {
                return gpBlock.alignment
            }
        }
        return null
    }


    @Suppress("UNUSED_PARAMETER")
    protected fun printChildAttributesContext(newChildIndex: Int) { // Needed for debug only
        /*
        println(this.javaClass.simpleName+"("+this.node.elementType+").getChildAttributes($newChildIndex)")
        subBlocks.mapIndexed { i, a -> println("$i $a")}
        */
    }

    companion object {
        fun hasLfBefore(psi: PsiElement): Boolean {
            var n = psi.prevSibling
            var r = false
            while (n is PsiComment || n is PsiWhiteSpace) {
                if (n is PsiWhiteSpace && n.textContains('\n')) {
                    r = true
                    break
                }
                n = n.prevSibling
            }
            return r
        }

        fun hasLfBefore(currBlock: Block): Boolean {
            var cB: Block? = currBlock
            while (cB is GroupBlock) {
                cB = cB.subBlocks.firstOrNull()
            }

            return if (cB is AbstractArendBlock) hasLfBefore(cB.node.psi) else false
        }
    }

}