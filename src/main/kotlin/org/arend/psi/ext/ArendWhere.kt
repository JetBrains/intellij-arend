package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.getChildrenOfType

class ArendWhere(node: ASTNode) : ArendCompositeElementImpl(node) {
    val statList: List<ArendStat>
        get() = getChildrenOfType()

    val lbrace: PsiElement?
        get() = findChildByType(LBRACE)

    val rbrace: PsiElement?
        get() = findChildByType(RBRACE)

    val whereKw: PsiElement?
        get() = findChildByType(WHERE_KW)
}