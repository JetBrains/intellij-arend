package org.arend.quickfix

import org.arend.util.ArendBundle

class ArgInferenceQuickFixTest : QuickFixTestBase() {

    fun testParameter() = typedQuickFixTest(ArendBundle.message("arend.argument.inference.parameter"), """
        \func f {A : Nat} (a : Nat) => A

        \func f1 => f 0
    """, """
        \func f {A : Nat} (a : Nat) => A

        \func f1 => f {0}
    """)
}
