package org.arend.psi.ext

import org.arend.term.abs.Abstract

interface ArendStatement : ArendCompositeElement, Abstract.Statement {
    override fun getGroup(): ArendGroup?
    override fun getNamespaceCommand(): ArendStatCmd?
    override fun getPLevelsDefinition(): ArendLevelParamsSeq?
    override fun getHLevelsDefinition(): ArendLevelParamsSeq?
}

interface ArendGroup: PsiLocatedReferable, ArendSourceNode, Abstract.Group {
    val where: ArendWhere?

    val parentGroup: ArendGroup?

    val internalReferables: List<ReferableBase<*>>

    override fun getStatements(): List<ArendStatement>

    override fun getDynamicSubgroups(): List<ArendGroup>
}