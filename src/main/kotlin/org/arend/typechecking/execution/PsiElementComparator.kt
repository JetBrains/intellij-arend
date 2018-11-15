package org.arend.typechecking.execution

import org.arend.naming.reference.TCReferable
import org.arend.psi.ext.ArendCompositeElement
import org.arend.typechecking.order.PartialComparator

object PsiElementComparator : PartialComparator<TCReferable> {
    override fun compare(t1: TCReferable?, t2: TCReferable?): PartialComparator.Result {
        if (t1 === t2) {
            return PartialComparator.Result.EQUALS
        }
        if (t1 == null || t2 == null) {
            return PartialComparator.Result.UNCOMPARABLE
        }

        val d1 = t1.data
        val d2 = t2.data
        if (!(d1 is ArendCompositeElement && d2 is ArendCompositeElement && d1.containingFile == d2.containingFile)) {
            return PartialComparator.Result.UNCOMPARABLE
        }

        val offset1 = d1.textOffset
        val offset2 = d2.textOffset
        return when {
            offset1 < offset2 -> PartialComparator.Result.LESS
            offset1 > offset2 -> PartialComparator.Result.GREATER
            else -> PartialComparator.Result.EQUALS
        }
    }
}