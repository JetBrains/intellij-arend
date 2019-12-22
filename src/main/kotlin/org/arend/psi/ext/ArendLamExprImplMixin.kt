package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.psi.ArendLamExpr
import org.arend.psi.ArendNameTele

abstract class ArendLamExprImplMixin(node: ASTNode) : ArendExprImplMixin(node), ArendLamExpr, Abstract.ParametersHolder {
    override fun <P : Any, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitLam(this, nameTeleList, expr, params)

    override fun getParameters(): List<ArendNameTele> = nameTeleList
}
