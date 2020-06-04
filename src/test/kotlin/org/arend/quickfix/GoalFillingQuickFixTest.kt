package org.arend.quickfix

import org.arend.ext.ArendExtension
import org.arend.ext.concrete.expr.ConcreteGoalExpression
import org.arend.ext.core.expr.CoreExpression
import org.arend.ext.typechecking.ExpressionTypechecker
import org.arend.ext.typechecking.GoalSolver
import org.arend.prelude.Prelude
import org.arend.term.concrete.Concrete

class GoalFillingQuickFixTest: QuickFixTestBase() {
    fun `test basic`() = typedQuickFixTest("Fill", """
        \func ok => {-caret-}{?(zero)}
    """, """
        \func ok => zero
    """)

    fun `test absent`() {
        typedCheckNoQuickFixes("Fill", """
        \func buxing : 114 = 514 => {-caret-}{?(zero)}
    """)
    }

    fun `test condition absent`() = typedCheckNoQuickFixes("Fill", """
        \func abs (i : Int) : Nat
          | pos a => a
          | neg a => {-caret-}{?(suc a)}
    """)

    private fun setGoalSolver() {
        library.arendExtension = object : ArendExtension {
            override fun getGoalSolver() = object : GoalSolver {
                override fun checkGoal(typechecker: ExpressionTypechecker, goalExpression: ConcreteGoalExpression, expectedType: CoreExpression?) =
                    GoalSolver.CheckGoalResult(Concrete.ReferenceExpression(null, Prelude.ZERO.ref), null)
            }
        }
    }

    fun `test custom fix`() {
        setGoalSolver()
        typedQuickFixTest("Fill", """
            \func test => {-caret-}{?}
        """, """
            \func test => zero
        """)
    }

    fun `test fix incomplete let`() {
        setGoalSolver()
        typedQuickFixTest("Fill", """
            \func test => \let x => 0{-caret-}
        """, """
            \func test => \let x => 0 \in zero
        """)
    }

    fun `test fix incomplete let 2`() {
        setGoalSolver()
        typedQuickFixTest("Fill", """
            \func test => \let x => 0 \in{-caret-}
            
        """, """
            \func test => \let x => 0 \in zero
            
        """)
    }
}