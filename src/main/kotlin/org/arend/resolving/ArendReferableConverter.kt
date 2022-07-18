package org.arend.resolving

import org.arend.naming.reference.*
import org.arend.psi.ext.PsiLocatedReferable


object ArendReferableConverter : BaseReferableConverter() {
    override fun toDataLocatedReferable(referable: LocatedReferable?): TCReferable? {
        return when (referable) {
            is PsiLocatedReferable -> referable.tcReferable
            is TCReferable -> referable
            else -> null
        }
    }
}
