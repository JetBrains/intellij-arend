package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.psi.ArendElementTypes
import org.arend.psi.getChildOfType

class ArendReturnExpr(node: ASTNode) : ArendCompositeElementImpl(node) {
    val type: ArendExpr?
        get() = getChildOfType()

    val typeLevel: ArendExpr?
        get() = getChildOfType(1)

    val levelKw: PsiElement?
        get() = findChildByType(ArendElementTypes.LEVEL_KW)
}