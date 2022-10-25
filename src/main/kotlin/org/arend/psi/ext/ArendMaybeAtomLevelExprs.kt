package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.getChildOfType
import org.arend.psi.getChildrenOfType

class ArendMaybeAtomLevelExprs(node: ASTNode) : ArendCompositeElementImpl(node), ArendTopLevelLevelExpr {
    val levelExprList: List<ArendLevelExpr>
        get() = maybeAtomLevelExpr?.atomLevelExpr?.let { listOf(it) } ?: getChildrenOfType()

    private val maybeAtomLevelExpr: ArendMaybeAtomLevelExpr?
        get() = getChildOfType()
}