package org.arend.quickfix

import org.arend.util.ArendBundle

class ImplicitLambdaQuickFixTest: QuickFixTestBase() {

    fun testLambda1() = typedQuickFixTest(
        ArendBundle.message("arend.lambda.argument.implicitness"), """
        \func test : \Pi {Nat} Nat {_} _ Nat -> Nat => \lam {a} b {c d{-caret-} : Nat} e => c
    """, """
        \func test : \Pi {Nat} Nat {_} _ Nat -> Nat => \lam {a} b {c : Nat} (d : Nat) e => c
    """
    )

    fun testLambda2() = typedQuickFixTest(
        ArendBundle.message("arend.lambda.argument.implicitness"), """
        \func test : \Pi Nat -> Nat => \lam {x{-caret-}} => x
    """, """
        \func test : \Pi Nat -> Nat => \lam (x) => x
    """
    )

    fun testLambda3() = typedQuickFixTest(
        ArendBundle.message("arend.lambda.argument.implicitness"), """
        \func F => \Pi Nat -> Nat
        \func test : F => \lam {x{-caret-}} => x
    """, """
        \func F => \Pi Nat -> Nat
        \func test : F => \lam (x) => x
    """
    )
}
