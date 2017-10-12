package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcLetClause
import org.vclang.psi.VcLetExpr

abstract class VcLetExprImplMixin(node: ASTNode) : VcExprImplMixin(node), VcLetExpr, Abstract.LetClausesHolder {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitLet(this, letClauseList, expr, params)

    override fun getLetClauses(): List<VcLetClause> = letClauseList
}
