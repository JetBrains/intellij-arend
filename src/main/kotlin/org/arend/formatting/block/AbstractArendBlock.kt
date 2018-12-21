package org.arend.formatting.block

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.formatter.common.AbstractBlock
import org.arend.psi.ArendArgumentAppExpr

abstract class AbstractArendBlock(node: ASTNode, val settings: CommonCodeStyleSettings?, wrap: Wrap?, alignment: Alignment?, private val myIndent: Indent?) : AbstractBlock(node, wrap, alignment) {
    override fun getIndent(): Indent? = myIndent

    override fun isLeaf(): Boolean = myNode.firstChildNode == null

    override fun getSpacing(child1: Block?, child2: Block): Spacing? = null

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes =
            ChildAttributes(Indent.getNoneIndent(), null)

    fun createArendBlock(childNode: ASTNode, childWrap: Wrap?, childAlignment: Alignment?, indent: Indent?): AbstractArendBlock {
        val childPsi = childNode.psi
        return if (childPsi is ArendArgumentAppExpr && childPsi.argumentList.isNotEmpty()) ArgumentAppExprBlock(childNode, settings, childWrap, childAlignment, indent)
        else SimpleArendBlock(childNode, settings, childWrap, childAlignment, indent)
    }


    protected fun printChildAttributesContext(newChildIndex: Int) { // Needed for debug only
        /*
        System.out.println(this.javaClass.simpleName+"("+this.node.elementType+").getChildAttributes($newChildIndex)")
        subBlocks.mapIndexed { i, a -> System.out.println("$i $a")}
        */
    }

    companion object {
        fun hasLfBefore(currBlock: Block) =
                if (currBlock is AbstractArendBlock) {
                    var n = currBlock.node.psi.prevSibling
                    var r = false
                    while (n is PsiComment || n is PsiWhiteSpace) {
                        if (n is PsiWhiteSpace && n.textContains('\n')) {
                            r = true
                            break
                        }
                        n = n.prevSibling
                    }
                    r
                } else false
    }

}