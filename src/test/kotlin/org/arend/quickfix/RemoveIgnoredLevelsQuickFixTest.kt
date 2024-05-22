package org.arend.quickfix

import org.arend.util.ArendBundle

class RemoveIgnoredLevelsQuickFixTest: QuickFixTestBase() {

    fun testRemoveIgnoredLevels() = typedQuickFixTest(
        ArendBundle.message("arend.remove.levels"), """
            \func foo (x : Nat) => x{-caret-} \levels 0 0
    """, """
            \func foo (x : Nat) => x
    """)
}
