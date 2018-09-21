package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.naming.reference.Referable
import org.arend.term.abs.Abstract
import org.arend.psi.ArendExpr


abstract class ArendExprImplMixin(node: ASTNode): ArendSourceNodeImpl(node), ArendExpr {
    override fun getData(): ArendExprImplMixin = this

    override fun isExplicit(): Boolean = true

    override fun getReferableList(): List<Referable?> = listOf(null)

    override fun getType(): Abstract.Expression = this
}