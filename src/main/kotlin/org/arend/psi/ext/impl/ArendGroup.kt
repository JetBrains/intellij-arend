package org.arend.psi.ext.impl

import org.arend.psi.ArendStatement
import org.arend.psi.ArendWhere
import org.arend.psi.ext.ArendSourceNode
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.term.group.ChildGroup
import org.arend.term.group.Group

interface ArendGroup: ChildGroup, PsiLocatedReferable, ArendSourceNode {
    val where: ArendWhere?

    val statements: List<ArendStatement>

    override fun getParentGroup(): ArendGroup?

    override fun getSubgroups(): List<ArendGroup>

    override fun getDynamicSubgroups(): List<ArendGroup>

    override fun getInternalReferables(): Collection<ArendInternalReferable>
}

interface ArendInternalReferable: Group.InternalReferable, PsiLocatedReferable {
    override fun getReferable(): PsiLocatedReferable
}