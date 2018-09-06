package com.jetbrains.arend.ide.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.arend.ide.psi.ArdPiExpr
import com.jetbrains.arend.ide.psi.ArdTypeTele
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor

abstract class ArdPiExprImplMixin(node: ASTNode) : ArdExprImplMixin(node), ArdPiExpr, Abstract.ParametersHolder {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
            visitor.visitPi(this, typeTeleList, expr, if (visitor.visitErrors()) com.jetbrains.arend.ide.psi.ext.getErrorData(this) else null, params)

    override fun getParameters(): List<ArdTypeTele> = typeTeleList
}
