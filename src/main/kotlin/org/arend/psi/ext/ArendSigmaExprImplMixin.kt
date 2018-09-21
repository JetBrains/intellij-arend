package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.psi.ArendSigmaExpr
import org.arend.psi.ArendTypeTele

abstract class ArendSigmaExprImplMixin(node: ASTNode) : ArendExprImplMixin(node), ArendSigmaExpr, Abstract.ParametersHolder {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitSigma(this, typeTeleList, if (visitor.visitErrors()) org.arend.psi.ext.getErrorData(this) else null, params)

    override fun getParameters(): List<ArendTypeTele> = typeTeleList
}
