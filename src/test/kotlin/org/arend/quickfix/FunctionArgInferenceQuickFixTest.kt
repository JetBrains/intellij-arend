package org.arend.quickfix

import org.arend.util.ArendBundle

class FunctionArgInferenceQuickFixTest : QuickFixTestBase() {

    fun testImplicitArgument() = typedQuickFixTest(ArendBundle.message("arend.argument.inference.parameter"), """
        \func f {A : Nat} (a : Nat) => A

        \func f1 => f{-caret-} 0
    """, """
        \func f {A : Nat} (a : Nat) => A

        \func f1 => f {{?}} 0
    """)

    fun testFunctionArgInference1() = typedQuickFixTest(ArendBundle.message("arend.argument.inference.parameter"), """
        \func f2 {A B C : \Type0} (a : A) (b : B) => a

        \func f3 => f2{-caret-} 0 0
    """, """
        \func f2 {A B C : \Type0} (a : A) (b : B) => a

        \func f3 => f2 {_} {_} {{?}} 0 0
    """)

    fun testFunctionArgInference2() = typedQuickFixTest(ArendBundle.message("arend.argument.inference.parameter"), """
        \func f4 {A B : \Type0} (a : A) {C : \Type1} (b : B) => a

        \func f5 => f4{-caret-} 0 0
    """, """
        \func f4 {A B : \Type0} (a : A) {C : \Type1} (b : B) => a

        \func f5 => f4 {_} {_} 0 {{?}} 0
    """)

    fun testInfixFunction() = typedQuickFixTest(ArendBundle.message("arend.argument.inference.parameter"), """
        \func \infixl 6 f6 {A B : \Type0} (a : A) => a

        \func f7 => f6{-caret-} 0
    """, """
        \func \infixl 6 f6 {A B : \Type0} (a : A) => a

        \func f7 => f6 {_} {{?}} 0
    """)


    /* doesn't work with lambda because error.definition is null
    * \func f => \lam {A B : \Type0} (a : A) {C : \Type1} (b : B) => a
    *
    * \func f1 => f 0 0
    */
}
