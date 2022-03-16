package org.arend.quickfix

import com.intellij.openapi.project.Project
import org.arend.ext.ArendExtension
import org.arend.ext.concrete.expr.ConcreteExpression
import org.arend.ext.concrete.expr.ConcreteGoalExpression
import org.arend.ext.core.expr.CoreExpression
import org.arend.ext.typechecking.ExpressionTypechecker
import org.arend.ext.typechecking.GoalSolver
import org.arend.ext.typechecking.InteractiveGoalSolver
import org.arend.ext.ui.ArendUI
import org.arend.psi.ArendPsiFactory
import org.arend.term.abs.ConcreteBuilder
import org.intellij.lang.annotations.Language
import java.util.function.Consumer

class InteractiveGoalSolverQuickFixTest : QuickFixTestBase() {
    fun `test caret is moved to the first goal`() = doTestWithGoalSolver(
        TestGoalSolver("suc {?}", project), """
            \func test : Nat => {-caret-}{?}
        """, """
            \func test : Nat => suc {-caret-}{?}
        """
    )

    fun `test caret is not moved when no goals`() = doTestWithGoalSolver(
        TestGoalSolver("zero", project), """
            \func test : Nat => {-caret-}{?}
        """, """
            \func test : Nat => {-caret-}zero
        """
    )

    fun `test caret is moved when result expression is wrapped in parens`() = doTestWithGoalSolver(
        TestGoalSolver("suc {?}", project), """
            \func test : Nat => suc {-caret-}{?}
        """, """
            \func test : Nat => suc (suc {-caret-}{?})
        """
    )

    private fun doTestWithGoalSolver(
        solver: InteractiveGoalSolver,
        @Language("Arend") before: String,
        @Language("Arend") after: String
    ) {
        library.arendExtension = object : ArendExtension {
            override fun getGoalSolver() = object : GoalSolver {
                override fun getAdditionalSolvers(): MutableCollection<out InteractiveGoalSolver> =
                    mutableListOf(solver)
            }
        }
        typedQuickFixTest(solver.shortDescription, before, after)
    }

    class TestGoalSolver(@Language("Arend") val solution: String, val project: Project) : InteractiveGoalSolver {
        override fun getShortDescription(): String = "Solve test goal"

        override fun isApplicable(goalExpression: ConcreteGoalExpression, expectedType: CoreExpression?): Boolean = true

        override fun solve(
            typechecker: ExpressionTypechecker,
            goalExpression: ConcreteGoalExpression,
            expectedType: CoreExpression?,
            ui: ArendUI,
            callback: Consumer<ConcreteExpression>
        ) {
            val exprPsi = ArendPsiFactory(project).createExpression(solution)
            val expr = ConcreteBuilder.convertExpression(exprPsi)
            callback.accept(expr)
        }
    }
}