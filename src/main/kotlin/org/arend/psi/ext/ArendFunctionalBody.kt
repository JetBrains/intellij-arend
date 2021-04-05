package org.arend.psi.ext

import com.intellij.psi.PsiElement
import org.arend.psi.*

interface ArendFunctionalBody : ArendCompositeElement {
    val clauseList: List<ArendClause>

    val coClauseList: List<ArendCoClause>

    val lbrace: PsiElement?

    val rbrace: PsiElement?

    val cowithKw: PsiElement?

    val fatArrow: PsiElement?

    val elim: ArendElim?

    val expr: ArendExpr?
}