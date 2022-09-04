package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.psi.ArendElementTypes
import org.arend.psi.getChildrenOfType

class ArendElim(node: ASTNode) : ArendCompositeElementImpl(node) {
    val refIdentifierList: List<ArendRefIdentifier>
        get() = getChildrenOfType()

    val elimKw: PsiElement?
        get() = findChildByType(ArendElementTypes.ELIM_KW)

    val withKw: PsiElement?
        get() = findChildByType(ArendElementTypes.WITH_KW)
}