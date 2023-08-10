package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.psi.ArendElementTypes
import org.arend.psi.childOfType

class ArendLevelsExpr(node: ASTNode) : ArendCompositeElementImpl(node) {
    val levelsExpr: ArendLevelsExpr?
        get() = childOfType()

    val pLevelExprs: ArendMaybeAtomLevelExprs?
        get() = childOfType()

    val hLevelExprs: ArendMaybeAtomLevelExprs?
        get() = childOfType(1)

    val levelsKw: PsiElement?
        get() = findChildByType(ArendElementTypes.LEVELS_KW)
}