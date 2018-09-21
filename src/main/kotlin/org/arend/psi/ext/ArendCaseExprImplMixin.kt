package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.psi.ArendCaseArg
import org.arend.psi.ArendCaseExpr


abstract class ArendCaseExprImplMixin(node: ASTNode) : ArendExprImplMixin(node), ArendCaseExpr, Abstract.CaseArgumentsHolder {
    override fun getCaseArguments(): List<ArendCaseArg> = caseArgList

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitCase(this, caseArgList, expr, clauseList, if (visitor.visitErrors()) org.arend.psi.ext.getErrorData(this) else null, params)
}