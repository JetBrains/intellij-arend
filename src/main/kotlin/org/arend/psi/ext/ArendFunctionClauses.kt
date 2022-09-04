package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.psi.ArendElementTypes
import org.arend.psi.getChildrenOfType

class ArendFunctionClauses(node: ASTNode) : ArendCompositeElementImpl(node) {
    val clauseList: List<ArendClause>
        get() = getChildrenOfType()

    val lbrace: PsiElement?
        get() = findChildByType(ArendElementTypes.LBRACE)
}