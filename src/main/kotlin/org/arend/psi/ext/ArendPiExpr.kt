package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.psi.getChildOfType
import org.arend.psi.getChildrenOfType

class ArendPiExpr(node: ASTNode) : ArendExpr(node), Abstract.ParametersHolder {
    val codomain: ArendExpr?
        get() = getChildOfType()

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitPi(this, parameters, codomain, params)

    override fun getParameters(): List<ArendTypeTele> = getChildrenOfType()
}
