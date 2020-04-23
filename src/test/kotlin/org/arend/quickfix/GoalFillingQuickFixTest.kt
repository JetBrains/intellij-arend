package org.arend.quickfix

class GoalFillingQuickFixTest: QuickFixTestBase() {
    fun `test basic`() = typedQuickFixTest("Fill", """
        \func ok => {-caret-}{?(zero)}
    """, """
        \func ok => zero
    """)

    fun `test absent`() = typedCheckNoQuickFixes("Fill", """
        \func buxing : 114 = 514 => {-caret-}{?(zero)}
    """)

    fun `test condition absent`() = typedCheckNoQuickFixes("Fill", """
        \func abs (i : Int) : Nat
          | pos a => a
          | neg a => {-caret-}{?(suc a)}
    """)
}