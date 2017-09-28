package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcArrExpr


abstract class VcArrExprImplMixin(node: ASTNode) : VcExprImplMixin(node), VcArrExpr {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val exprs = exprList
        return visitor.visitPi(this, if (exprs.isEmpty()) emptyList() else listOf(exprs[0]), exprs.getOrNull(1), params)
    }
}