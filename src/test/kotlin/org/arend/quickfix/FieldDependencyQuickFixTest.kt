package org.arend.quickfix

import org.arend.util.ArendBundle

class FieldDependencyQuickFixTest: QuickFixTestBase() {

    fun testFieldDependency1() = typedQuickFixTest(
        ArendBundle.message("arend.field.dependency"), """
        \record R (x : Nat) (p : x = x)
        \func f (r : R) => \new r { | {-caret-}x => 2 }
    """, """
        \record R (x : Nat) (p : x = x)
        \func f (r : R) => \new r { | x => 2 | p => {?}{-caret-} }
    """)

    fun testFieldDependency2() = typedQuickFixTest(
        ArendBundle.message("arend.field.dependency"), """
        \record R (x : Nat) (p : x = x)
        \func f (r : R 2) => \new r { | {-caret-}x => 2 }
    """, """
        \record R (x : Nat) (p : x = x)
        \func f (r : R 2) => \new r { | x => 2 | p => {?}{-caret-} }
    """)
}
