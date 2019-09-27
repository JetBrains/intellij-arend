package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.util.PsiTreeUtil
import org.arend.psi.ArendCaseArg
import org.arend.psi.ArendCaseExpr
import org.arend.psi.ArendExpr
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractExpressionVisitor


abstract class ArendCaseExprImplMixin(node: ASTNode) : ArendExprImplMixin(node), ArendCaseExpr {
    override fun getCaseArguments(): List<ArendCaseArg> = caseArgList

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val returnExprs = PsiTreeUtil.getChildrenOfTypeAsList(returnExpr, ArendExpr::class.java)
        val evalKind = casePrefix.let {
            when {
                it.pevalKw != null -> Abstract.EvalKind.PEVAL
                it.evalKw != null -> Abstract.EvalKind.EVAL
                else -> null
            }
        }
        return visitor.visitCase(this, caseOpt.scaseKw != null, evalKind, caseArgList, returnExprs.firstOrNull(), returnExprs.getOrNull(1), clauseList, if (visitor.visitErrors()) getErrorData(this) else null, params)
    }
}