package org.arend.resolving

import org.arend.naming.reference.*
import org.arend.psi.ArendDefIdentifier
import org.arend.psi.ext.ArendLevelParamImplMixin
import org.arend.psi.ext.PsiLocatedReferable


object ArendReferableConverter : BaseReferableConverter() {
    override fun toDataLocatedReferable(referable: LocatedReferable?): TCReferable? =
        when (referable) {
            is PsiLocatedReferable -> referable.tcReferable
            is TCReferable -> referable
            else -> null
        }

    override fun toDataLevelReferable(referable: Referable?): LevelReferable? = when (referable) {
        is ArendDefIdentifier -> (referable.parent as? ArendLevelParamImplMixin)?.levelRef
        is LevelReferable -> referable
        else -> null
    }
}
