package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.childOfType
import org.arend.psi.childOfTypeStrict
import org.arend.term.abs.Abstract

class ArendSuperClass(node: ASTNode) : ArendSourceNodeImpl(node), Abstract.ReferenceExpression {
    val longName: ArendLongName
        get() = childOfTypeStrict()

    override fun getData() = this

    override fun getReferent() = longName.referent

    private fun getLevels(index: Int) = childOfType<ArendMaybeAtomLevelExprs>(index)?.levelExprList

    override fun getPLevels(): List<ArendLevelExpr>? = getLevels(0)

    override fun getHLevels(): List<ArendLevelExpr>? = getLevels(1)
}