package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.psi.ArendElementTypes
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.psi.getChildOfType
import org.arend.psi.getChildOfTypeStrict
import org.arend.term.abs.Abstract


class ArendTupleExpr(node: ASTNode) : ArendSourceNodeImpl(node), Abstract.Expression {
    val expr: ArendExpr
        get() = getChildOfTypeStrict()

    val type: ArendExpr?
        get() = getChildOfType(1)

    val colon: PsiElement?
        get() = findChildByType(ArendElementTypes.COLON)

    val exprIfSingle: ArendExpr?
        get() = if (colon != null) null else expr

    override fun getData() = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val expr = expr
        val type = type
        return if (type == null) expr.accept(visitor, params) else visitor.visitTyped(this, expr, type, params)
    }
}