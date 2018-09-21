package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.psi.ArendArgumentAppExpr


abstract class ArendArgumentAppExprImplMixin(node: ASTNode) : ArendExprImplMixin(node), ArendArgumentAppExpr {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val expr = atomFieldsAcc ?: longNameExpr ?: error("Incomplete expression: " + this)
        val args = argumentList
        return if (args.isEmpty()) expr.accept(visitor, params) else visitor.visitBinOpSequence(this, expr, args, if (visitor.visitErrors()) org.arend.psi.ext.getErrorData(this) else null, params)
    }
}