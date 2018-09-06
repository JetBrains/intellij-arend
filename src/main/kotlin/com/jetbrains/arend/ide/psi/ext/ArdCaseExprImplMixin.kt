package com.jetbrains.arend.ide.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.arend.ide.psi.ArdCaseArg
import com.jetbrains.arend.ide.psi.ArdCaseExpr
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor


abstract class ArdCaseExprImplMixin(node: ASTNode) : ArdExprImplMixin(node), ArdCaseExpr, Abstract.CaseArgumentsHolder {
    override fun getCaseArguments(): List<ArdCaseArg> = caseArgList

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
            visitor.visitCase(this, caseArgList, expr, clauseList, if (visitor.visitErrors()) com.jetbrains.arend.ide.psi.ext.getErrorData(this) else null, params)
}