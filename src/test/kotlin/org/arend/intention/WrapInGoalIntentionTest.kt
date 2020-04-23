package org.arend.intention

import org.arend.quickfix.QuickFixTestBase

class WrapInGoalIntentionTest: QuickFixTestBase() {
    fun `test basic`() = simpleQuickFixTest("Wrap", """
        \func gkd => {-caret-}zero
    """, """
        \func gkd => {?(zero)}
    """)

    fun `test absence when on goal`() = checkNoQuickFixes("Wrap", """
        \func kkp => {-caret-}{?(zero)}
    """)

    fun `test absence when in goal`() = checkNoQuickFixes("Wrap", """
        \func sdl => {?({-caret-}zero)}
    """)

    fun `test presence when deeper in goal`() = simpleQuickFixTest("Wrap", """
        \func awsl : Nat => {?(\case 1 \with {
          | zero => {-caret-}0
          | suc a => a
        })}
    ""","""
        \func awsl : Nat => {?(\case 1 \with {
          | zero => {?(0)}
          | suc a => a
        })}
    """)
}