package com.jetbrains.arend.ide.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.arend.ide.psi.ArdAppExpr
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor


abstract class ArdAppExprImplMixin(node: ASTNode) : ArdExprImplMixin(node), ArdAppExpr {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
            visitor.visitInferHole(this, if (visitor.visitErrors()) com.jetbrains.arend.ide.psi.ext.getErrorData(this) else null, params)
}
