package org.arend.formatting.block

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.formatter.common.AbstractBlock

abstract class AbstractArendBlock(node: ASTNode, wrap: Wrap?, alignment: Alignment?, private val myIndent: Indent?): AbstractBlock(node, wrap, alignment) {
    override fun getIndent(): Indent? = myIndent

    override fun isLeaf(): Boolean = myNode.firstChildNode == null

    override fun getSpacing(child1: Block?, child2: Block): Spacing? = null

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes =
            ChildAttributes(Indent.getNoneIndent(), null)
}