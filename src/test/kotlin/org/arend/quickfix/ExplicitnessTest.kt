package org.arend.quickfix

import org.arend.util.ArendBundle

class ExplicitnessTest : QuickFixTestBase() {

    fun testExplicitness() = typedQuickFixTest(ArendBundle.message("arend.argument.explicitness"), """
        \func f (a : Nat) => a

        \func f1 => f {0} 0
    """, """
        \func f (a : Nat) => a

        \func f1 => f 0
    """)

    fun testImplicitness() = typedQuickFixTest(ArendBundle.message("arend.argument.implicitness"), """
        \record record
            | field {A : \Type} : A

        \func test : record \cowith
            | field \as \fix 5 f t => {?}
    """, """
        \record record
            | field {A : \Type} : A

        \func test : record \cowith
            | field \as \fix 5 f {t} => {?}
    """)
}
