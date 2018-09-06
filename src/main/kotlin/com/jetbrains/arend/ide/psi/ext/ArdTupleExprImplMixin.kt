package com.jetbrains.arend.ide.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.arend.ide.psi.ArdTupleExpr
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor


abstract class ArdTupleExprImplMixin(node: ASTNode) : ArdSourceNodeImpl(node), ArdTupleExpr {
    override fun getData() = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val exprs = exprList
        return when {
            exprs.isEmpty() -> visitor.visitInferHole(this, if (visitor.visitErrors()) com.jetbrains.arend.ide.psi.ext.getErrorData(this) else null, params)
            exprs.size == 1 -> exprs[0].accept(visitor, params)
            else -> visitor.visitTyped(this, exprs[0], exprs[1], if (visitor.visitErrors()) com.jetbrains.arend.ide.psi.ext.getErrorData(this) else null, params)
        }
    }
}