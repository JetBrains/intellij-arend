package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.naming.reference.Referable
import org.arend.psi.ArendMetaPLevelsSeq
import org.arend.term.abs.Abstract

abstract class ArendMetaPLevelsSeqImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendMetaPLevelsSeq {
    override fun getData() = this

    override fun getReferables(): List<Referable> = pLevelIdentifierList

    override fun getComparisonList(): List<Abstract.Comparison> = emptyList()

    override fun isIncreasing() = true
}