package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.psi.ArendLamExpr
import org.arend.psi.ArendLamParam
import org.arend.psi.ArendLamTele

abstract class ArendLamExprImplMixin(node: ASTNode) : ArendExprImplMixin(node), ArendLamExpr, Abstract.LamParametersHolder {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitLam(this, lamParamList, expr, params)

    override fun getParameters(): List<ArendLamTele> = lamParamList.filterIsInstance<ArendLamTele>()

    override fun getLamParameters(): List<ArendLamParam> = lamParamList
}
