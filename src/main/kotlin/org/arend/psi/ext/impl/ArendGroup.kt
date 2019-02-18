package org.arend.psi.ext.impl

import org.arend.psi.ArendWhere
import org.arend.psi.ext.ArendSourceNode
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.term.group.ChildGroup
import org.arend.term.group.Group

interface ArendGroup: ChildGroup, PsiLocatedReferable, ArendSourceNode {
    val where: ArendWhere?

    override fun getInternalReferables(): Collection<ArendInternalReferable>
}

interface ArendInternalReferable: Group.InternalReferable, PsiLocatedReferable {
    override fun getReferable(): PsiLocatedReferable
}