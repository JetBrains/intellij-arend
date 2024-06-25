package org.arend.resolving

import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.TCReferable

interface IntellijTCReferable : TCReferable {
    fun isEquivalent(ref: LocatedReferable): Boolean
    val isConsistent: Boolean
    var displayName: String?
}