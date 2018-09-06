package com.jetbrains.arend.ide.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.arend.ide.psi.ArdLetClause
import com.jetbrains.arend.ide.psi.ArdLetExpr
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor

abstract class ArdLetExprImplMixin(node: ASTNode) : ArdExprImplMixin(node), ArdLetExpr, Abstract.LetClausesHolder {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
            visitor.visitLet(this, letClauseList, expr, if (visitor.visitErrors()) com.jetbrains.arend.ide.psi.ext.getErrorData(this) else null, params)

    override fun getLetClauses(): List<ArdLetClause> = letClauseList
}
