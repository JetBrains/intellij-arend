package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.getChildOfType
import org.arend.psi.getChildOfTypeStrict
import org.arend.term.abs.Abstract

class ArendSuperClass(node: ASTNode) : ArendSourceNodeImpl(node), Abstract.ReferenceExpression {
    val longName: ArendLongName
        get() = getChildOfTypeStrict()

    override fun getData() = this

    override fun getReferent() = longName.referent

    private fun getLevels(index: Int) = getChildOfType<ArendMaybeAtomLevelExprs>(index)?.levelExprList

    override fun getPLevels(): List<ArendLevelExpr>? = getLevels(0)

    override fun getHLevels(): List<ArendLevelExpr>? = getLevels(1)
}