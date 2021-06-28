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
import org.arend.core.expr.visitor.FreeVariablesCollector
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
        val expectedGoalType = (arendError.error as? GoalError)?.expectedType
        val collector = FreeVariablesWithDependenciesCollector()
        expectedGoalType?.accept(collector, null)
        val freeVariables = collector.freeBindings.toList()
        // todo: implicitness
        insertDefinition(freeVariables, goal, editor)
    }


    private fun insertDefinition(freeVariables : List<Binding>, goal: PsiElement, editor: Editor) {
        val function = goal.parentOfType<ArendFunctionalDefinition>() ?: return
        // todo: editable definition name
        // todo: editable parameters
        var newName = "ipsum"
        var newFunction = "\\func $newName"
        for (binding in freeVariables) {
            newName += " ${binding.name}"
            newFunction += " (${binding.name} : ${binding.typeExpr})"
        }
        newFunction += " => {?}"
        editor.document.insertString(function.endOffset, "\n$newFunction")
        editor.document.replaceString(goal.startOffset, goal.endOffset, newName)
    }
}

class FreeVariablesWithDependenciesCollector : VoidExpressionVisitor<Void>() {
    val freeBindings : MutableSet<Binding> = LinkedHashSet()

    override fun visitReference(expr: ReferenceExpression, params: Void?): Void? {
        val currentBinding = expr.binding
        currentBinding.type.expr.accept(this, null)
        freeBindings.add(currentBinding)
        return null
    }

    override fun visitClassCall(expr: ClassCallExpression, params: Void): Void? {
        visitDefCall(expr, params)
        for ((_, value) in expr.implementedHere) {
            value.accept(this, params)
        }
        freeBindings.remove(expr.thisBinding)
        return null
    }

    override fun visitSubst(expr: SubstExpression, params: Void?): Void? {
        expr.expression.accept(this, params)
        freeBindings.removeAll(expr.substitution.keys)
        for ((_, value) in expr.substitution.entries) {
            value.accept(this, params)
        }
        return null
    }

    private fun freeParams(param: DependentLink) {
        var currentParam = param
        while (currentParam.hasNext()) {
            freeBindings.remove(currentParam)
            currentParam = currentParam.next
        }
    }

    override fun visitLam(expr: LamExpression, params: Void?): Void? {
        super.visitLam(expr, null)
        freeParams(expr.parameters)
        return null
    }

    override fun visitPi(expr: PiExpression, params: Void?): Void? {
        super.visitPi(expr, null)
        freeParams(expr.parameters)
        return null
    }

    override fun visitSigma(expr: SigmaExpression, params: Void?): Void? {
        super.visitSigma(expr, null)
        freeParams(expr.parameters)
        return null
    }

    override fun visitLet(expr: LetExpression, params: Void?): Void? {
        super.visitLet(expr, null)
        for (clause in expr.clauses) {
            freeBindings.remove(clause)
        }
        return null
    }

    override fun visitElimBody(elimBody: ElimBody, params: Void?) {
        super.visitElimBody(elimBody, params)
        for (clause in elimBody.clauses) {
            freeParams(clause.parameters)
        }
    }

    override fun visitCase(expr: CaseExpression, params: Void?): Void? {
        super.visitCase(expr, null)
        freeParams(expr.parameters)
        return null
    }
}
