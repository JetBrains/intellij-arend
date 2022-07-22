package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.naming.reference.Referable
import org.arend.psi.ArendPLevelParamsSeq
import org.arend.term.abs.Abstract

abstract class ArendPLevelParamsSeqImplMixin(node: ASTNode) : ArendCompositeElementImpl(node), ArendPLevelParamsSeq {
    override fun getData() = this

    override fun getReferables(): List<Referable> = pLevelIdentifierList

    override fun getComparisonList(): List<Abstract.Comparison> = levelCmpList.map {
        if (it.greaterOrEquals != null) Abstract.Comparison.GREATER_OR_EQUALS else Abstract.Comparison.LESS_OR_EQUALS
    }

    override fun isIncreasing() = levelCmpList.firstOrNull()?.lessOrEquals == null
}