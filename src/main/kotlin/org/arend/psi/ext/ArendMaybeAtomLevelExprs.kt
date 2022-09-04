package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.getChildOfType
import org.arend.psi.getChildrenOfType

class ArendMaybeAtomLevelExprs(node: ASTNode) : ArendCompositeElementImpl(node), ArendTopLevelLevelExpr {
    val levelExprList: List<ArendLevelExpr>
        get() = getChildrenOfType()

    val maybeAtomLevelExpr: ArendMaybeAtomLevelExpr?
        get() = getChildOfType()
}