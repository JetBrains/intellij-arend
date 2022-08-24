package org.arend.quickfix

import org.arend.ext.concrete.definition.FunctionKind
import org.arend.util.ArendBundle

class ReplaceDefKindQuickFix : QuickFixTestBase() {
    fun testCouldBeLemma() = simpleQuickFixTest(ArendBundle.message("arend.replace.function.kind", FunctionKind.LEMMA),
        """
            \lemma lem : 0 = 0 => idp
            {-caret-}\func test => lem
        """.trimIndent(),
        """
            \lemma lem : 0 = 0 => idp
            \lemma test => lem
        """.trimIndent())

    fun testLevelMismatchFunction() = simpleQuickFixTest(ArendBundle.message("arend.replace.function.kind", FunctionKind.FUNC),
            """
                {-caret-}\lemma test => 0
            """.trimIndent(),
            """
                \func test => 0
            """.trimIndent())

    fun testLevelMismatchFunction2() = simpleQuickFixTest(ArendBundle.message("arend.replace.function.kind", FunctionKind.FUNC),
        """
            {-caret-}\lemma test : Nat => 0
        """.trimIndent(),
        """
            \func test => 0
        """.trimIndent())

    fun testLevelMismatchField() = simpleQuickFixTest(ArendBundle.message("arend.replace.field.kind"),
            """
                \record R {
                  {-caret-}\property f : Nat
                }
            """.trimIndent(),
            """
                \record R {
                  | f : Nat
                }
            """.trimIndent())

    fun testLevelMismatchSigma() = simpleQuickFixTest(ArendBundle.message("arend.replace.sigma.kind"),
            """
                \func test => \Sigma (x : Nat) ({-caret-}\property Nat)
            """.trimIndent(),
            """
                \func test => \Sigma (x : Nat) (Nat)
            """.trimIndent())

    fun testLevelMismatchSigma2() = simpleQuickFixTest(ArendBundle.message("arend.replace.sigma.kind"),
            """
                \func test => \Sigma (x : Nat) ({-caret-}\property y : Nat)
            """.trimIndent(),
            """
                \func test => \Sigma (x : Nat) (y : Nat)
            """.trimIndent())
}