package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.naming.reference.Referable
import org.arend.psi.ArendMetaHLevelsSeq
import org.arend.term.abs.Abstract

abstract class ArendMetaHLevelsSeqImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendMetaHLevelsSeq {
    override fun getData() = this

    override fun getReferables(): List<Referable> = hLevelIdentifierList

    override fun getComparisonList(): List<Abstract.Comparison> = emptyList()

    override fun isIncreasing() = true
}
