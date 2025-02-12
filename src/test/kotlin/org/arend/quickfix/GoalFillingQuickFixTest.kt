package org.arend.quickfix

import org.arend.ext.ArendExtension
import org.arend.ext.concrete.expr.ConcreteGoalExpression
import org.arend.ext.core.expr.CoreExpression
import org.arend.ext.typechecking.ExpressionTypechecker
import org.arend.ext.typechecking.GoalSolver
import org.arend.prelude.Prelude
import org.arend.term.concrete.Concrete
import org.arend.util.ArendBundle

class GoalFillingQuickFixTest: QuickFixTestBase() {
    val fixName = ArendBundle.message("arend.expression.fillGoal")

    private fun doTest(contents: String, result: String) = typedQuickFixTest(fixName, contents, result)

    private fun doTestWithGoalSolver(contents: String, result: String) {
        setGoalSolver()
        doTest(contents, result)
    }

    private fun testNoQuickFixes(contents: String) = typedCheckNoQuickFixes(fixName, contents)

    fun `test basic`() = doTest("""
        \func ok => {-caret-}{?(zero)}
    """, """
        \func ok => zero
    """)

    fun `test absent`() = testNoQuickFixes("""
        \func buxing : 114 = 514 => {-caret-}{?(zero)}
    """)

    fun `test condition absent`() = testNoQuickFixes("""
        \func abs (i : Int) : Nat
          | pos a => a
          | neg a => {-caret-}{?(suc a)}
    """)

    private fun setGoalSolver() {
        /* TODO[server2]
        library.arendExtension = object : ArendExtension {
            override fun getGoalSolver() = object : GoalSolver {
                override fun checkGoal(typechecker: ExpressionTypechecker, goalExpression: ConcreteGoalExpression, expectedType: CoreExpression?) =
                    GoalSolver.CheckGoalResult(Concrete.ReferenceExpression(null, Prelude.ZERO.ref), null)
            }
        }
        */
    }

    fun `test custom fix`() = doTestWithGoalSolver("""
            \func test => {-caret-}{?}
        """, """
            \func test => zero
        """)

    fun `test fix incomplete let`() = doTestWithGoalSolver("""
            \func test => \let x => 0{-caret-}
        """, """
            \func test => \let x => 0 \in zero
        """)

    fun `test fix incomplete let 2`() = doTestWithGoalSolver("""
            \func test => \let x => 0 \in{-caret-}
            
        """, """
            \func test => \let x => 0 \in zero
            
        """)

    fun `test fix incomplete lam`() = doTestWithGoalSolver("""
            \func test : Nat -> Nat => \lam x{-caret-}
        """, """
            \func test : Nat -> Nat => \lam x => zero
        """)

    fun `test fix incomplete lam 2`() = doTestWithGoalSolver("""
            \func test : Nat -> Nat => \lam x =>{-caret-}
            
        """, """
            \func test : Nat -> Nat => \lam x => zero
            
        """)

    fun `test fix incomplete tuple`() = doTestWithGoalSolver("""
            \func test => (1,{-caret-})
        """, """
            \func test => (1,zero)
        """)

    fun `test fix incomplete implicit tuple`() = doTestWithGoalSolver("""
            \func f {p : \Sigma Nat Nat} => p.1
            \func test => f {1,{-caret-}}
        """, """
            \func f {p : \Sigma Nat Nat} => p.1
            \func test => f {1,zero}
        """)

    fun `test fix incomplete implicit tuple with indent`() = doTestWithGoalSolver("""
            \func f {p : \Sigma Nat Nat} => p.1
            \func test => f {
              1,
              {-caret-}
            }
        """.trimIndent(), """
            \func f {p : \Sigma Nat Nat} => p.1
            \func test => f {
              1,
              zero
            }
        """.trimIndent())
}