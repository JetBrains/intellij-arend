package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcSigmaExpr
import org.vclang.psi.VcTele

abstract class VcSigmaExprImplMixin(node: ASTNode) : VcExprImplMixin(node), VcSigmaExpr, Abstract.ParametersHolder {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitSigma(this, teleList, params)

    override fun getParameters(): List<VcTele> = teleList
}
