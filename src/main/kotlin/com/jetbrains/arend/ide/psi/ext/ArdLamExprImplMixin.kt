package com.jetbrains.arend.ide.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.arend.ide.psi.ArdLamExpr
import com.jetbrains.arend.ide.psi.ArdNameTele
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor

abstract class ArdLamExprImplMixin(node: ASTNode) : ArdExprImplMixin(node), ArdLamExpr, Abstract.ParametersHolder {
    override fun <P : Any, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
            visitor.visitLam(this, nameTeleList, expr, if (visitor.visitErrors()) com.jetbrains.arend.ide.psi.ext.getErrorData(this) else null, params)

    override fun getParameters(): List<ArdNameTele> = nameTeleList
}
