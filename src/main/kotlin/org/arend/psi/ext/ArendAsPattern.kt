package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.childOfType
import org.arend.term.abs.Abstract


class ArendAsPattern(node: ASTNode) : ArendSourceNodeImpl(node), Abstract.TypedReferable {
    override fun getData() = this

    override fun getReferable(): ArendDefIdentifier? = childOfType()

    override fun getType(): ArendExpr? = childOfType()
}