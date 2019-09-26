package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.ArendEvalExpr
import org.arend.term.abs.AbstractExpressionVisitor


abstract class ArendEvalExprImplMixin(node: ASTNode) : ArendExprImplMixin(node), ArendEvalExpr {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitEval(this, pevalKw != null, argumentAppExpr, if (visitor.visitErrors()) getErrorData(this) else null, params)
}