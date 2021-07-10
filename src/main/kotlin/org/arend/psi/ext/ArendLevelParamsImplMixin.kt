package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.naming.reference.Referable
import org.arend.psi.ArendLevelParams
import org.arend.term.abs.Abstract

abstract class ArendLevelParamsImplMixin(node: ASTNode) : ArendCompositeElementImpl(node), ArendLevelParams {
    override fun getData() = this

    override fun getReferables(): List<Referable> = levelParamList.map { it.defIdentifier }

    override fun getComparisonList(): List<Abstract.Comparison> = levelCmpList.map {
        if (it.greaterOrEquals != null) Abstract.Comparison.GREATER_OR_EQUALS else Abstract.Comparison.LESS_OR_EQUALS
    }
}