package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractExpressionVisitor


class ArendCaseExpr(node: ASTNode) : ArendExpr(node), Abstract.CaseArgumentsHolder {
    val returnExpr: ArendReturnExpr?
        get() = childOfType()

    val withBody: ArendWithBody?
        get() = childOfType()

    val caseKw: PsiElement?
        get() = getChild { it.elementType == CASE_KW || it.elementType == SCASE_KW }

    val returnKw: PsiElement?
        get() = findChildByType(RETURN_KW)

    override fun getCaseArguments(): List<ArendCaseArg> = getChildrenOfType()

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val child = firstRelevantChild
        val evalKind = when (child.elementType) {
            PEVAL_KW -> Abstract.EvalKind.PEVAL
            EVAL_KW -> Abstract.EvalKind.EVAL
            BOX_KW -> Abstract.EvalKind.BOX
            else -> null
        }
        val returnExpr = returnExpr
        return visitor.visitCase(this, caseKw?.elementType == SCASE_KW, evalKind, caseArguments, returnExpr?.type, returnExpr?.typeLevel, withBody?.clauseList ?: emptyList(), params)
    }
}