package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcArgumentAppExpr


abstract class VcArgumentAppExprImplMixin(node: ASTNode) : VcExprImplMixin(node), VcArgumentAppExpr {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val expr = atomFieldsAcc ?: longNameExpr ?: error("Incomplete expression: " + this)
        val args = argumentList
        return if (args.isEmpty()) expr.accept(visitor, params) else visitor.visitBinOpSequence(this, expr, args, if (visitor.visitErrors()) org.vclang.psi.ext.getErrorData(this) else null, params)
    }
}