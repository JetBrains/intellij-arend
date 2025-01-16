package org.arend.psi.ext

import com.intellij.openapi.components.service
import org.arend.server.ArendServerService
import org.arend.term.abs.Abstract
import org.arend.term.group.ChildGroup
import org.arend.term.group.ConcreteGroup
import org.arend.term.group.Group
import org.arend.term.group.Statement

interface ArendStatement : Statement, ArendCompositeElement, Abstract.Statement {
    override fun getGroup(): ArendGroup?
    override fun getNamespaceCommand(): ArendStatCmd?
    override fun getPLevelsDefinition(): ArendLevelParamsSeq?
    override fun getHLevelsDefinition(): ArendLevelParamsSeq?
}

interface ArendGroup: ChildGroup, PsiDefReferable, ArendSourceNode, Abstract.Group {
    val where: ArendWhere?

    override fun getStatements(): List<ArendStatement>

    override fun getParentGroup(): ArendGroup?

    override fun getDynamicSubgroups(): List<ArendGroup>

    override fun getInternalReferables(): List<ArendInternalReferable>
}

interface ArendInternalReferable: Group.InternalReferable, PsiDefReferable {
    override fun getReferable(): PsiDefReferable
}

val ArendGroup.concreteGroup: ConcreteGroup? get() {
    val path = groupPath ?: return null
    return project.service<ArendServerService>().server.getGroup(path.module)?.getSubgroup(path)
}