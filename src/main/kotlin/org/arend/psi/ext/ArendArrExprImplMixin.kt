package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.psi.ArendArrExpr


abstract class ArendArrExprImplMixin(node: ASTNode) : ArendExprImplMixin(node), ArendArrExpr {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val exprs = exprList
        return visitor.visitPi(this, if (exprs.isEmpty()) emptyList() else listOf(exprs[0]), exprs.getOrNull(1), params)
    }
}