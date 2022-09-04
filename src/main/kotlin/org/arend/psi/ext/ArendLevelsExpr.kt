package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.psi.ArendElementTypes
import org.arend.psi.getChildOfType

class ArendLevelsExpr(node: ASTNode) : ArendCompositeElementImpl(node) {
    val levelsExpr: ArendLevelsExpr?
        get() = getChildOfType()

    val pLevelExprs: ArendMaybeAtomLevelExprs?
        get() = getChildOfType()

    val hLevelExprs: ArendMaybeAtomLevelExprs?
        get() = getChildOfType(1)

    val levelsKw: PsiElement?
        get() = findChildByType(ArendElementTypes.LEVELS_KW)
}