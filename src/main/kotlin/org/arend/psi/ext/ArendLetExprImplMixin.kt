package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.psi.ArendLetClause
import org.arend.psi.ArendLetExpr

abstract class ArendLetExprImplMixin(node: ASTNode) : ArendExprImplMixin(node), ArendLetExpr, Abstract.LetClausesHolder {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitLet(this, letsKw != null, letClauseList, expr, params)

    override fun getLetClauses(): List<ArendLetClause> = letClauseList
}
