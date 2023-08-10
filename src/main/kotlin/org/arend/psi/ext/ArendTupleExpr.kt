package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.psi.ArendElementTypes
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.psi.childOfType
import org.arend.psi.childOfTypeStrict
import org.arend.term.abs.Abstract


class ArendTupleExpr(node: ASTNode) : ArendSourceNodeImpl(node), Abstract.Expression {
    val expr: ArendExpr
        get() = childOfTypeStrict()

    val type: ArendExpr?
        get() = childOfType(1)

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