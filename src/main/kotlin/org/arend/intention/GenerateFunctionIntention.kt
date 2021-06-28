package org.arend.intention

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.arend.core.context.binding.Binding
import org.arend.core.context.param.DependentLink
import org.arend.core.elimtree.ElimBody
import org.arend.core.expr.*
import org.arend.core.expr.visitor.VoidExpressionVisitor
import org.arend.psi.ArendGoal
import org.arend.psi.ext.ArendFunctionalDefinition
import org.arend.psi.parentOfType
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.error.local.GoalError
import org.arend.util.ArendBundle

class GenerateFunctionIntention : BaseArendIntention(ArendBundle.message("arend.generate.function")) {
    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        return element.parentOfType<ArendGoal>(false) != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        editor ?: return
        val goal = element.parentOfType<ArendGoal>(false) ?: return
        val errorService = project.service<ErrorService>()
        val arendError = errorService.errors[element.containingFile]?.firstOrNull { it.cause == goal } ?: return
        val expectedGoalType = (arendError.error as? GoalError)?.expectedType ?: return
        val freeVariables = FreeVariablesWithDependenciesCollector.collectFreeVariables(expectedGoalType)
        insertDefinition(freeVariables, goal, editor)
    }


    private fun insertDefinition(freeVariables : List<Pair<Binding, ParameterExplicitnessState>>, goal: PsiElement, editor: Editor) {
        val function = goal.parentOfType<ArendFunctionalDefinition>() ?: return
        // todo: editable definition name
        // todo: editable parameters
        var newName = "ipsum"
        var newFunction = "\\func $newName"
        for ((binding, explicitness) in freeVariables) {
            if (explicitness == ParameterExplicitnessState.EXPLICIT) {
                newName += " ${binding.name}"
            }
            newFunction += " ${explicitness.openBrace}${binding.name} : ${binding.typeExpr}${explicitness.closingBrace}"
        }
        // todo: return type
        newFunction += " => {?}"
        editor.document.insertString(function.endOffset, "\n$newFunction")
        editor.document.replaceString(goal.startOffset, goal.endOffset, newName)
    }
}

enum class ParameterExplicitnessState(val openBrace : String, val closingBrace : String) {
    EXPLICIT("(", ")"), MAYBE_IMPLICIT("{", "}")
}

class FreeVariablesWithDependenciesCollector private constructor() : VoidExpressionVisitor<ParameterExplicitnessState>() {
    companion object {
        fun collectFreeVariables(expression : Expression) : List<Pair<Binding, ParameterExplicitnessState>> =
            FreeVariablesWithDependenciesCollector()
                .apply { expression.accept(this, ParameterExplicitnessState.EXPLICIT) }
                .apply {
                    val allImplicitBindings = freeBindings.mapNotNull { if (it.second == ParameterExplicitnessState.MAYBE_IMPLICIT) it.first else null }
                    // todo: some parameters cannot be implicit even if they are in types (like pi or sigma)
                    freeBindings.removeIf { it.first in toRemove || (it.second == ParameterExplicitnessState.EXPLICIT && it.first in allImplicitBindings) }
                }
                .freeBindings
                .toList()
    }

    private val freeBindings : MutableSet<Pair<Binding, ParameterExplicitnessState>> = LinkedHashSet()
    private val toRemove : MutableSet<Binding> = HashSet()

    override fun visitReference(expr: ReferenceExpression, state: ParameterExplicitnessState): Void? {
        val currentBinding = expr.binding
        currentBinding.type.expr.accept(this, ParameterExplicitnessState.MAYBE_IMPLICIT)
        freeBindings.add(currentBinding to state)
        return null
    }

    override fun visitClassCall(expr: ClassCallExpression, state: ParameterExplicitnessState): Void? {
        visitDefCall(expr, state)
        for ((_, value) in expr.implementedHere) {
            value.accept(this, state)
        }
        toRemove.add(expr.thisBinding)
        return null
    }

    override fun visitSubst(expr: SubstExpression, state: ParameterExplicitnessState): Void? {
        expr.expression.accept(this, state)
        toRemove.addAll(expr.substitution.keys)
        for ((_, value) in expr.substitution.entries) {
            value.accept(this, state)
        }
        return null
    }

    private fun freeParams(param: DependentLink) {
        var currentParam = param
        while (currentParam.hasNext()) {
            toRemove.add(currentParam)
            currentParam = currentParam.next
        }
    }

    override fun visitLam(expr: LamExpression, state: ParameterExplicitnessState): Void? {
        super.visitLam(expr, state)
        freeParams(expr.parameters)
        return null
    }

    override fun visitPi(expr: PiExpression, state: ParameterExplicitnessState): Void? {
        super.visitPi(expr, state)
        freeParams(expr.parameters)
        return null
    }

    override fun visitSigma(expr: SigmaExpression, state: ParameterExplicitnessState): Void? {
        super.visitSigma(expr, state)
        freeParams(expr.parameters)
        return null
    }

    override fun visitLet(expr: LetExpression, state: ParameterExplicitnessState): Void? {
        super.visitLet(expr, state)
        for (clause in expr.clauses) {
            toRemove.add(clause)
        }
        return null
    }

    override fun visitElimBody(elimBody: ElimBody, state: ParameterExplicitnessState) {
        super.visitElimBody(elimBody, state)
        for (clause in elimBody.clauses) {
            freeParams(clause.parameters)
        }
    }

    override fun visitCase(expr: CaseExpression, state: ParameterExplicitnessState): Void? {
        super.visitCase(expr, state)
        freeParams(expr.parameters)
        return null
    }
}
