package org.arend.typechecking.execution

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.SmartPsiElementPointer
import org.arend.naming.reference.TCDefReferable
import org.arend.psi.ext.ArendCompositeElement
import org.arend.typechecking.order.PartialComparator

object PsiElementComparator : PartialComparator<TCDefReferable> {
    override fun compare(t1: TCDefReferable?, t2: TCDefReferable?): PartialComparator.Result {
        if (t1 === t2) {
            return PartialComparator.Result.EQUALS
        }
        if (t1 == null || t2 == null) {
            return PartialComparator.Result.UNCOMPARABLE
        }

        val p1 = t1.data
        val p2 = t2.data
        return runReadAction {
            val d1 = (p1 as? SmartPsiElementPointer<*>)?.element ?: p1
            val d2 = (p2 as? SmartPsiElementPointer<*>)?.element ?: p2
            if (!(d1 is ArendCompositeElement && d2 is ArendCompositeElement && d1.containingFile == d2.containingFile)) {
                PartialComparator.Result.UNCOMPARABLE
            } else {
                val offset1 = d1.textOffset
                val offset2 = d2.textOffset
                when {
                    offset1 < offset2 -> PartialComparator.Result.LESS
                    offset1 > offset2 -> PartialComparator.Result.GREATER
                    else -> PartialComparator.Result.EQUALS
                }
            }
        }
    }
}