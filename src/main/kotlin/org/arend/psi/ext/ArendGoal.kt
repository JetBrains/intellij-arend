package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.getChildOfType

class ArendGoal(node: ASTNode) : PsiReferableImpl(node) {
    val defIdentifier: ArendDefIdentifier?
        get() = getChildOfType()

    val expr: ArendExpr?
        get() = getChildOfType()
}