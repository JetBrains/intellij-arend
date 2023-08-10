package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.childOfType
import org.arend.psi.getChildrenOfType
import org.arend.term.abs.Abstract

class ArendLevelParamsSeq(node: ASTNode) : ArendCompositeElementImpl(node), Abstract.LevelParameters {
    override fun getData() = this

    override fun getReferables(): List<ArendLevelIdentifier> = getChildrenOfType()

    override fun getComparisonList(): List<Abstract.Comparison> = getChildrenOfType<ArendLevelCmp>().map {
        if (it.isIncreasing) Abstract.Comparison.LESS_OR_EQUALS else Abstract.Comparison.GREATER_OR_EQUALS
    }

    override fun isIncreasing() = childOfType<ArendLevelCmp>()?.isIncreasing != false
}