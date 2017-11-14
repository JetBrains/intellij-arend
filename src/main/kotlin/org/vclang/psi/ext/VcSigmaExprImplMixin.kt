package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcSigmaExpr
import org.vclang.psi.VcTypeTele

abstract class VcSigmaExprImplMixin(node: ASTNode) : VcExprImplMixin(node), VcSigmaExpr, Abstract.ParametersHolder {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitSigma(this, typeTeleList, params)

    override fun getParameters(): List<VcTypeTele> = typeTeleList
}
