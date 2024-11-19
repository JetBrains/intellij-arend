package org.arend.intention

import org.arend.quickfix.QuickFixTestBase
import org.arend.util.ArendBundle

class WrapInGoalIntentionTest: QuickFixTestBase() {
    private val fixName = ArendBundle.message("arend.expression.wrapInGoal")
    private fun doTest(contents: String, result: String) = simpleQuickFixTest(fixName, contents, result)
    private fun testNoQuickFixes(contents: String) = checkNoQuickFixes(fixName, contents)

    fun `test basic`() = doTest("""
        \func gkd => {-caret-}{-selection-}zero{-end_selection-}
    """, """
        \func gkd => {?(zero)}
    """)

    fun `test absense without selection`() = testNoQuickFixes("""
        \func gkd => {-caret-}zero
    """)

    fun `test absence when on goal`() = testNoQuickFixes( """
        \func kkp => {-caret-}{-selection-}{?(zero)}{-end_selection-}
    """)

    fun `test absence when in goal`() = testNoQuickFixes( """
        \func sdl => {?({-caret-}zero)}
    """)

    fun `test presence when deeper in goal`() = doTest("""
        \func awsl : Nat => {?(\case 1 \with {
          | zero => {-caret-}{-selection-}0{-end_selection-}
          | suc a => a
        })}
    ""","""
        \func awsl : Nat => {?(\case 1 \with {
          | zero => {?(0)}
          | suc a => a
        })}
    """)

    fun `test with end marker`() {
        configure("""
            \data List
                | nil
                | cons Nat List
            
            \func plus (a b : Prelude.Nat) => a Nat.+ b
            
            \func lol => {-selection-}cons (plus 2 3) (cons (plus 2 3) nil){-caret-}{-end_selection-}
        """)
        assertNotNull(myFixture.findSingleIntention(fixName))
    }
}