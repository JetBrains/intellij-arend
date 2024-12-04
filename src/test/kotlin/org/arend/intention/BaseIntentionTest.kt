package org.arend.intention

import org.arend.quickfix.QuickFixTestBase
import org.arend.util.ArendBundle

class BaseIntentionTest : QuickFixTestBase() {
    fun `test wrap in goal with the end marker`() {
        configure("""
            \data List
                | nil
                | cons Nat List
            
            \func plus (a b : Prelude.Nat) => a Nat.+ b
            
            \func lol => {-selection-}cons (plus 2 3) (cons (plus 2 3) nil){-caret-}{-end_selection-}
        """)
        assertNotNull(myFixture.findSingleIntention(ArendBundle.message("arend.expression.wrapInGoal")))
    }
}
