package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.naming.reference.Referable
import org.arend.psi.ArendHLevelParamsSeq
import org.arend.term.abs.Abstract

abstract class ArendHLevelParamsSeqImplMixin(node: ASTNode) : ArendCompositeElementImpl(node), ArendHLevelParamsSeq {
    override fun getData() = this

    override fun getReferables(): List<Referable> = hLevelIdentifierList

    override fun getComparisonList(): List<Abstract.Comparison> = levelCmpList.map {
        if (it.greaterOrEquals != null) Abstract.Comparison.GREATER_OR_EQUALS else Abstract.Comparison.LESS_OR_EQUALS
    }

    override fun isIncreasing() = levelCmpList.firstOrNull()?.lessOrEquals == null
}