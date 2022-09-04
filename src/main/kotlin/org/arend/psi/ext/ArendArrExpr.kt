package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.getChildOfType
import org.arend.term.abs.AbstractExpressionVisitor


class ArendArrExpr(node: ASTNode) : ArendExpr(node) {
    val domain: ArendExpr?
        get() = getChildOfType()

    val codomain: ArendExpr?
        get() = getChildOfType(1)

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val domain = domain
        return visitor.visitPi(this, if (domain == null) emptyList() else listOf(domain), codomain, params)
    }
}