package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.psi.ArendElementTypes
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.psi.getChildrenOfType

class ArendTuple(node: ASTNode) : ArendExpr(node) {
    val tupleExprList: List<ArendTupleExpr>
        get() = getChildrenOfType()

    val lparen: PsiElement?
        get() = findChildByType(ArendElementTypes.LPAREN)

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        acceptTupleExpression(this, tupleExprList, visitor, params)
}