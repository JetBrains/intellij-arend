package org.arend.psi.ext

import com.intellij.psi.PsiElement
import org.arend.naming.reference.Referable
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.term.abs.Abstract


interface CoClauseBase : Abstract.ClassFieldImpl, ArendCompositeElement {
    val localCoClauseList: List<ArendLocalCoClause>
        get() = getChildrenOfType()

    val longName: ArendLongName?
        get() = childOfType()

    val lamParamList: List<ArendLamParam>
        get() = getChildrenOfType()

    val resolvedImplementedField: Referable?
        get() = longName?.refIdentifierList?.lastOrNull()?.reference?.resolve() as? Referable

    val expr: ArendExpr?
        get() = childOfType()

    val lbrace: PsiElement?
        get() = childOfType(LBRACE)

    val rbrace: PsiElement?
        get() = childOfType(RBRACE)

    val fatArrow: PsiElement?
        get() = childOfType(FAT_ARROW)
}