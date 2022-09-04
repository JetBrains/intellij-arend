package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.psi.ArendElementTypes
import org.arend.psi.getChildOfType

class ArendReplLine(node: ASTNode) : ArendCompositeElementImpl(node) {
    val expr: ArendExpr?
        get() = getChildOfType()

    val replCommand: PsiElement?
        get() = findChildByType(ArendElementTypes.REPL_COMMAND)
}