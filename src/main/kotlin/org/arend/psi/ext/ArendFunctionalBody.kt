package org.arend.psi.ext

import com.intellij.psi.PsiElement
import org.arend.psi.ArendCoClause
import org.arend.psi.ArendElim
import org.arend.psi.ArendExpr
import org.arend.psi.ArendFunctionClauses

interface ArendFunctionalBody : ArendCompositeElement {
    val coClauseList: List<ArendCoClause>

    val lbrace: PsiElement?

    val rbrace: PsiElement?

    val cowithKw: PsiElement?

    val fatArrow: PsiElement?

    val functionClauses: ArendFunctionClauses?

    val elim: ArendElim?

    val expr: ArendExpr?
}