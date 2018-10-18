package org.arend.formatting.block

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.formatter.common.AbstractBlock
import com.intellij.psi.TokenType.WHITE_SPACE
import org.arend.psi.ArendCoClause
import java.util.ArrayList

class SimpleArendBlock(node: ASTNode, wrap: Wrap?, alignment: Alignment?, private val myIndent: Indent? = Indent.getNoneIndent()): AbstractBlock(node, wrap, alignment) {
    override fun isLeaf(): Boolean = myNode.firstChildNode == null

    override fun getIndent(): Indent? = myIndent

    override fun getSpacing(child1: Block?, child2: Block): Spacing? = null

    override fun buildChildren(): MutableList<Block> {
        val blocks = ArrayList<Block>()
        var child: ASTNode? = myNode.firstChildNode

        while (child != null) {
            if (child.elementType != WHITE_SPACE) {
                var indent: Indent? = Indent.getNoneIndent()
                if (child.psi is ArendCoClause) indent = Indent.getNormalIndent()
                val block = SimpleArendBlock(child, null, null, indent)
                blocks.add(block)
            }
            child = child.treeNext
        }
        return blocks
    }

    fun getIndent(str : String): Int? {
        var myStr = str
        if (myStr.indexOf('\n') == -1) return null
        while (myStr.indexOf('\n') != -1) myStr = myStr.substring(myStr.indexOf('\n')+1)
        return myStr.length
    }
}