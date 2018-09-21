package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.psi.ArendTupleExpr


abstract class ArendTupleExprImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendTupleExpr {
    override fun getData() = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val exprs = exprList
        return when {
            exprs.isEmpty() -> visitor.visitInferHole(this, if (visitor.visitErrors()) org.arend.psi.ext.getErrorData(this) else null, params)
            exprs.size == 1 -> exprs[0].accept(visitor, params)
            else -> visitor.visitTyped(this, exprs[0], exprs[1], if (visitor.visitErrors()) org.arend.psi.ext.getErrorData(this) else null, params)
        }
    }
}