package org.arend.quickfix

import org.arend.util.ArendBundle

class RemoveClassifyingFieldQuickFixTest: QuickFixTestBase() {

    fun testRemoveClassifyingField() = typedQuickFixTest(
        ArendBundle.message("arend.classifying.remove"), """
            \record C (n : Nat) (\classifying{-caret-} A : \Type) | a : A -> 0 = n
    """, """
            \record C (n : Nat) (A : \Type) | a : A -> 0 = n
    """)
}
