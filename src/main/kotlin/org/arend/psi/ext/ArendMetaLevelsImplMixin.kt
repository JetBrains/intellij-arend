package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.naming.reference.Referable
import org.arend.psi.ArendMetaLevels
import org.arend.term.abs.Abstract

abstract class ArendMetaLevelsImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendMetaLevels {
    override fun getData() = this

    override fun getReferables(): List<Referable> = defIdentifierList

    override fun getComparisonList(): List<Abstract.Comparison> = emptyList()
}