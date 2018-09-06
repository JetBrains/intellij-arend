package com.jetbrains.arend.ide.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.arend.ide.psi.ArdExpr
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.term.abs.Abstract


abstract class ArdExprImplMixin(node: ASTNode) : ArdSourceNodeImpl(node), ArdExpr {
    override fun getData(): ArdExprImplMixin = this

    override fun isExplicit(): Boolean = true

    override fun getReferableList(): List<Referable?> = listOf(null)

    override fun getType(): Abstract.Expression = this
}