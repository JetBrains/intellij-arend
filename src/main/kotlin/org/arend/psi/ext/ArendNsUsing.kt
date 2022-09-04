package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.getChildrenOfType

class ArendNsUsing(node: ASTNode) : ArendCompositeElementImpl(node) {
    val lparen: PsiElement?
        get() = findChildByType(LPAREN)

    val rparen: PsiElement?
        get() = findChildByType(RPAREN)

    val usingKw: PsiElement?
        get() = findChildByType(USING_KW)

    val nsIdList: List<ArendNsId>
        get() = getChildrenOfType()
}