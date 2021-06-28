package org.arend.intention

import org.arend.quickfix.QuickFixTestBase
import org.arend.util.ArendBundle

class GenerateFunctionIntentionTest : QuickFixTestBase() {
    private fun doTest(contents: String, result: String) =
        simpleQuickFixTest(ArendBundle.message("arend.generate.function"), contents.trimIndent(), result.trimIndent())


    fun `test basic`() = doTest("""
        \func lorem {A : \Type} (x y : A) : x = y => {{-caret-}?}
    """, """
        \func lorem {A : \Type} (x y : A) : x = y => ipsum x y
        \func ipsum {A : \Type} (x : A) (y : A) : x = y => {?}
        """
    )

    fun `test dependent type`() = doTest("""
        \func lorem {A : \Type} {B : A -> \Type} {a : A} (x y : B a) : x = y => {{-caret-}?}
    """, """
        \func lorem {A : \Type} {B : A -> \Type} {a : A} (x y : B a) : x = y => ipsum x y
        \func ipsum {A : \Type} {B : A -> \Type} {a : A} (x : B a) (y : B a) : x = y => {?}
        """
    )
}
