package org.arend.psi.ext.impl

import org.arend.psi.ArendWhere
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.term.group.ChildGroup

interface ArendGroup: ChildGroup, PsiLocatedReferable {
    val where: ArendWhere?
}