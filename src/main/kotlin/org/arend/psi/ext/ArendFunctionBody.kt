package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.childOfType
import org.arend.psi.getChildrenOfType

class ArendFunctionBody(node: ASTNode, val kind: Kind) : ArendCompositeElementImpl(node) {
    enum class Kind { FUNCTION, INSTANCE, COCLAUSE }

    val functionClauses: ArendFunctionClauses?
        get() = childOfType()

    val clauseList: List<ArendClause>
        get() = if (kind == Kind.COCLAUSE) getChildrenOfType() else functionClauses?.clauseList ?: emptyList()

    val coClauseList: List<ArendCoClause>
        get() = getChildrenOfType()

    val lbrace: PsiElement?
        get() = findChildByType(LBRACE)

    val fatArrow: PsiElement?
        get() = findChildByType(FAT_ARROW)

    val cowithKw: PsiElement?
        get() = findChildByType(COWITH_KW)

    val elim: ArendElim?
        get() = childOfType()

    val expr: ArendExpr?
        get() = childOfType()
}