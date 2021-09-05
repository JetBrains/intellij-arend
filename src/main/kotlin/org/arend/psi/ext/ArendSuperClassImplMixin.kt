package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.ArendLevelExpr
import org.arend.psi.ArendSuperClass

abstract class ArendSuperClassImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendSuperClass {
    override fun getData() = this

    override fun getReferent() = longName.referent

    override fun getPLevels(): Collection<ArendLevelExpr>? = maybeAtomLevelExprsList.getOrNull(0)?.levelExprList

    override fun getHLevels(): Collection<ArendLevelExpr>? = maybeAtomLevelExprsList.getOrNull(1)?.levelExprList
}