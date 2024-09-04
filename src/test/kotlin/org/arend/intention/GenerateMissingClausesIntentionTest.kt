package org.arend.intention

import org.arend.quickfix.QuickFixTestBase
import org.arend.util.ArendBundle

class GenerateMissingClausesIntentionTest : QuickFixTestBase() {
    private val fixMissingClauses = ArendBundle.message("arend.generatePatternMatchingClauses")
    private val fixElimMissingClauses = ArendBundle.message("arend.generateElimPatternMatchingClauses")

    fun testGenerateMissingClauses() = simpleQuickFixTest(
        fixMissingClauses,
        """
            \data Bool | false | true
            
            \func foo (n m : Nat) : Bool => {{-caret-}?}
        """,
        """
            \data Bool | false | true

            \func foo (n m : Nat) : Bool
              | 0, 0 => {?}
              | 0, suc m => {?}
              | suc n, 0 => {?}
              | suc n, suc m => {?}
        """
    )

    fun testGenerateElimMissingClauses() = simpleQuickFixTest(
        fixElimMissingClauses,
        """
            \data Bool | false | true
            
            \func foo (x y : Bool): Bool => {{-caret-}?}
        """,
        """
            \data Bool | false | true

            \func foo (x y : Bool): Bool \elim
        """
    )
}
