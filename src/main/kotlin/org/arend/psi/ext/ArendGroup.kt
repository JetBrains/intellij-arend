package org.arend.psi.ext

import org.arend.ext.prettyprinting.doc.Doc
import org.arend.term.abs.Abstract
import org.arend.term.group.ChildGroup
import org.arend.term.group.Group
import org.arend.term.group.Statement

interface ArendStatement : Statement, ArendCompositeElement, Abstract.Statement {
    override fun getGroup(): ArendGroup?
    override fun getNamespaceCommand(): ArendStatCmd?
    override fun getPLevelsDefinition(): ArendLevelParamsSeq?
    override fun getHLevelsDefinition(): ArendLevelParamsSeq?
}

interface ArendGroup: ChildGroup, PsiLocatedReferable, ArendSourceNode, Abstract.Group {
    val where: ArendWhere?

    override fun getStatements(): List<ArendStatement>

    override fun getParentGroup(): ArendGroup?

    override fun getDynamicSubgroups(): List<ArendGroup>

    override fun getInternalReferables(): List<ArendInternalReferable>

    override fun getDescription(): Doc
}

interface ArendInternalReferable: Group.InternalReferable, PsiLocatedReferable {
    override fun getReferable(): PsiLocatedReferable

    override fun isVisible(): Boolean
}