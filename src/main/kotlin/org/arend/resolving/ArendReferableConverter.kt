package org.arend.resolving

import org.arend.naming.reference.*
import org.arend.psi.ext.PsiLocatedReferable


object ArendReferableConverter : BaseReferableConverter() {
    override fun toDataLocatedReferable(referable: LocatedReferable?): TCReferable? {
        return when (referable) {
            is PsiLocatedReferable -> LocatedReferableImpl(referable, referable.accessModifier, referable.precedence, referable.refName, referable.aliasPrecedence, referable.aliasName, toDataLocatedReferable(referable.locatedReferableParent), referable.kind)
            is TCReferable -> referable
            else -> null
        }
    }
}
