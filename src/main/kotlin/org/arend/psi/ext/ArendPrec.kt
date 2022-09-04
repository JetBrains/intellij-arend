package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.psi.ArendElementTypes

class ArendPrec(node: ASTNode) : ArendCompositeElementImpl(node) {
    val number: PsiElement?
        get() = findChildByType(ArendElementTypes.NUMBER)
}