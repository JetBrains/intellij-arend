package com.jetbrains.arend.ide.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.arend.ide.psi.ArdArrExpr
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor


abstract class ArdArrExprImplMixin(node: ASTNode) : ArdExprImplMixin(node), ArdArrExpr {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val exprs = exprList
        return visitor.visitPi(this, if (exprs.isEmpty()) emptyList() else listOf(exprs[0]), exprs.getOrNull(1), if (visitor.visitErrors()) com.jetbrains.arend.ide.psi.ext.getErrorData(this) else null, params)
    }
}