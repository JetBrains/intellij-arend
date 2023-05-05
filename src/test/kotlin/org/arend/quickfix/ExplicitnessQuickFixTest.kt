package org.arend.quickfix

import org.arend.util.ArendBundle

class ExplicitnessQuickFixTest : QuickFixTestBase() {

    fun testExplicitness1() = typedQuickFixTest(ArendBundle.message("arend.argument.explicitness"), """
        \func f (a : Nat) => a
        \func f1 => f {0{-caret-}}
    """, """
        \func f (a : Nat) => a
        \func f1 => f 0
    """)

    fun testExplicitness2() = typedQuickFixTest(ArendBundle.message("arend.argument.explicitness"), """
        \func f (a : Nat) (b : Nat) => a
        \func f1 => f {f 0 1{-caret-}} 1
    """, """
        \func f (a : Nat) (b : Nat) => a
        \func f1 => f (f 0 1) 1
    """)

    fun testExplicitness3() = typedQuickFixTest(ArendBundle.message("arend.argument.explicitness"), """
        \func f (A : \Type) (a : A) => a
        \func g => f {_{-caret-}} 0
    """, """
        \func f (A : \Type) (a : A) => a
        \func g => f _ 0
    """)

    fun testImplicitness() = typedQuickFixTest(ArendBundle.message("arend.argument.implicitness"), """
        \record record
            | field {A : \Type} : A

        \func test : record \cowith
            | field \as \fix 5 f t{-caret-} => {?}
    """, """
        \record record
            | field {A : \Type} : A

        \func test : record \cowith
            | field \as \fix 5 f {t} => {?}
    """)
}
