package org.arend.psi.ext

import org.arend.naming.reference.TCDefReferable

interface TCDefinition : PsiConcreteReferable {
    override val tcReferable: TCDefReferable?
}
