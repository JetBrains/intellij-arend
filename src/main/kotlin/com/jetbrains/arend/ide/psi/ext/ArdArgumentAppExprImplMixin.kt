package com.jetbrains.arend.ide.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.arend.ide.psi.ArdArgumentAppExpr
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor


abstract class ArdArgumentAppExprImplMixin(node: ASTNode) : ArdExprImplMixin(node), ArdArgumentAppExpr {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val expr = atomFieldsAcc ?: longNameExpr ?: error("Incomplete expression: " + this)
        val args = argumentList
        return if (args.isEmpty()) expr.accept(visitor, params) else visitor.visitBinOpSequence(this, expr, args, if (visitor.visitErrors()) com.jetbrains.arend.ide.psi.ext.getErrorData(this) else null, params)
    }
}