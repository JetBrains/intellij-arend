package org.arend.intention

import org.arend.quickfix.QuickFixTestBase

class WrapInGoalIntentionTest: QuickFixTestBase() {
    fun `test basic`() = simpleQuickFixTest("Wrap", """
        \func gkd => {-caret-}{-selection-}zero{-end_selection-}
    """, """
        \func gkd => {?(zero)}
    """)

    fun `test absense without selection`() = checkNoQuickFixes("Wrap", """
        \func gkd => {-caret-}zero
    """)

    fun `test absence when on goal`() = checkNoQuickFixes("Wrap", """
        \func kkp => {-caret-}{-selection-}{?(zero)}{-end_selection-}
    """)

    fun `test absence when in goal`() = checkNoQuickFixes("Wrap", """
        \func sdl => {?({-caret-}zero)}
    """)

    fun `test presence when deeper in goal`() = simpleQuickFixTest("Wrap", """
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
}