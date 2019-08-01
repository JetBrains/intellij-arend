package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.ArendExpr


abstract class ArendExprImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendExpr {
    override fun getData() = this

    override fun isExplicit() = true

    override fun getReferableList() = listOf(null)

    override fun getType() = this
}