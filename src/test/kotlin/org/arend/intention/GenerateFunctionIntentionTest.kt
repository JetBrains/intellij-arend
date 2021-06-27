package org.arend.intention

import org.arend.quickfix.QuickFixTestBase
import org.arend.util.ArendBundle

class GenerateFunctionIntentionTest : QuickFixTestBase() {
    private fun doTest(contents: String, result: String) = simpleQuickFixTest(ArendBundle.message("arend.generate.function"), contents, result)


    fun test() = doTest(
        """
        \func lorem {A : \Type} (x y : A) : x = y => {{-caret-}?}
    """.trimIndent(), """
        \func lorem {A : \Type} (x y : A) : x = y => ipsum y x A
        \func ipsum (y : A) (x : A) (A : \Type) => {?}

        """.trimIndent()
    )
}
