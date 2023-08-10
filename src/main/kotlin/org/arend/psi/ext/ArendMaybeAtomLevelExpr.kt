package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.childOfType

class ArendMaybeAtomLevelExpr(node: ASTNode) : ArendCompositeElementImpl(node) {
    val atomLevelExpr: ArendAtomLevelExpr?
        get() = childOfType()
}