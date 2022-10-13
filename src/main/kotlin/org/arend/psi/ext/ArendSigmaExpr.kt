package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.psi.getChildrenOfType

class ArendSigmaExpr(node: ASTNode) : ArendExpr(node), Abstract.ParametersHolder {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitSigma(this, parameters, params)

    override fun getParameters(): List<ArendTypeTele> = getChildrenOfType()
}
