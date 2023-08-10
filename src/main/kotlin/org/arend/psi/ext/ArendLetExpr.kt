package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractExpressionVisitor

class ArendLetExpr(node: ASTNode) : ArendExpr(node), Abstract.LetClausesHolder {
    val expr: ArendExpr?
        get() = childOfType()

    val inKw: PsiElement?
        get() = findChildByType(IN_KW)

    override fun getLetClauses(): List<ArendLetClause> = getChildrenOfType()

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val type = firstRelevantChild.elementType
        return visitor.visitLet(this, type == HAVE_KW || type == HAVES_KW, type == HAVES_KW || type == LETS_KW, letClauses, expr, params)
    }
}
