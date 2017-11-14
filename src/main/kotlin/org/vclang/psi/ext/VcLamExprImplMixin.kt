package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcLamExpr
import org.vclang.psi.VcNameTele

abstract class VcLamExprImplMixin(node: ASTNode) : VcExprImplMixin(node), VcLamExpr, Abstract.ParametersHolder {
    override fun <P : Any, R : Any> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitLam(this, nameTeleList, expr, params)

    override fun getParameters(): List<VcNameTele> = nameTeleList
}
