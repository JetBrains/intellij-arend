package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.childOfType

class ArendGoal(node: ASTNode) : PsiReferableImpl(node) {
    val defIdentifier: ArendDefIdentifier?
        get() = childOfType()

    val expr: ArendExpr?
        get() = childOfType()
}