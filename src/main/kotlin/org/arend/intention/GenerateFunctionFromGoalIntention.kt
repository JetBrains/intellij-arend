package org.arend.intention

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import org.arend.core.context.binding.TypedBinding
import org.arend.core.expr.DefCallExpression
import org.arend.core.expr.Expression
import org.arend.core.expr.PiExpression
import org.arend.core.expr.ReferenceExpression
import org.arend.extImpl.UncheckedExpressionImpl
import org.arend.psi.ArendArgument
import org.arend.psi.ArendArgumentAppExpr
import org.arend.psi.ArendAtomFieldsAcc
import org.arend.psi.ArendGoal
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendFunctionalDefinition
import org.arend.refactoring.tryCorrespondedSubExpr
import org.arend.term.concrete.BaseConcreteExpressionVisitor
import org.arend.term.concrete.Concrete
import org.arend.term.concrete.ConcreteExpressionVisitor
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.error.local.GoalError
import org.arend.util.ArendBundle
import org.arend.util.ParameterExplicitnessState
import org.arend.util.appExprToConcrete
import kotlin.math.exp

class GenerateFunctionFromGoalIntention : AbstractGenerateFunctionIntention() {

    override fun getText(): String = ArendBundle.message("arend.generate.function.from.goal")

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        editor ?: return false
        file ?: return false
        if (!canModify(file) || !BaseArendIntention.canModify(file)) {
            return false
        }
        return file.findElementAt(editor.caretModel.offset)?.parentOfType<ArendGoal>(false) != null
    }

    override fun extractSelectionData(file: PsiFile, editor: Editor, project: Project): SelectionResult? {
        val goal = file.findElementAt(editor.caretModel.offset)?.parentOfType<ArendGoal>(false) ?: return null
        val errorService = project.service<ErrorService>()
        val arendError = errorService.errors[goal.containingFile]?.firstOrNull { it.cause == goal }?.error as? GoalError
                ?: return null
        val goalType = (arendError as? GoalError)?.expectedType
        val appParent = goal.parentOfType<ArendArgumentAppExpr>()?.takeIf { it.atomFieldsAcc?.atom?.literal?.goal === goal }
        val additionalArguments = mutableListOf<Pair<TypedBinding, ParameterExplicitnessState>>()
        var targetCodomain = goalType?.deepCodomain()
        if (appParent != null && goalType is PiExpression) {
            var currentGoalType : Expression? = goalType
            val forbiddenNames = mutableSetOf<String>()
            val actualArguments = getBinOpAwareArguments(goal, appParent)
            val typecheckedUserArguments = actualArguments.map { (elem, explicit) -> tryCorrespondedSubExpr(elem.textRange, file, project, editor) to explicit }
            for ((argResult, explicitness) in typecheckedUserArguments) {
                currentGoalType as PiExpression
                val expectedType = currentGoalType.parameters.typeExpr
                val explicitnessState = if (explicitness) ParameterExplicitnessState.EXPLICIT else ParameterExplicitnessState.IMPLICIT
                if (argResult == null) {
                    additionalArguments.add(TypedBinding("_", expectedType) to explicitnessState)
                } else {
                    val contextElement = goal.parentOfType<ArendFunctionalDefinition>() ?: goal
                    val newBinding = TypedBinding(suggestParameterName(forbiddenNames, contextElement, expectedType), expectedType)
                    additionalArguments.add(newBinding to explicitnessState)
                    val uncheckedTargetExpression = targetCodomain?.replaceSubexpressions { expr ->
                        if (expr == argResult.subCore) {
                            ReferenceExpression(newBinding)
                        } else {
                            null
                        }
                    }
                    targetCodomain = UncheckedExpressionImpl.extract(uncheckedTargetExpression)
                }
                currentGoalType = currentGoalType.codomain
            }
        }
        val goalExpr = goal.expr?.let {
            tryCorrespondedSubExpr(it.textRange, file, project, editor)
        }?.subCore
        return SelectionResult(targetCodomain, goal, goal.textRange, null, goal.defIdentifier?.name, goalExpr, additionalArguments)
    }

    private fun Expression.deepCodomain() : Expression =
        if (this is PiExpression) codomain.deepCodomain() else this

    private fun getBinOpAwareArguments(goal: ArendGoal, psiAppExpr: ArendArgumentAppExpr) : List<Pair<PsiElement, Boolean>> {
        val parsedAppExpr = appExprToConcrete(psiAppExpr, true) ?: return psiAppExpr.argumentList.map { it to it.isExplicit }
        var result = emptyList<Pair<PsiElement, Boolean>>()
        parsedAppExpr.accept(object : BaseConcreteExpressionVisitor<Unit>() {
            override fun visitApp(expr: Concrete.AppExpression, params: Unit?): Concrete.Expression {
                val functionPsi = expr.function.data as PsiElement
                if (functionPsi is ArendAtomFieldsAcc && PsiTreeUtil.isAncestor(functionPsi, goal, false)) {
                    result = expr.arguments.map { it.expression.data as PsiElement to it.isExplicit }
                    return expr
                }
                return super.visitApp(expr, params)
            }
        }, Unit)
        return result
    }

    private fun suggestParameterName(forbiddenNames: MutableSet<String>, context : ArendCompositeElement, type : Expression) : String {
        var candidate = if (type is DefCallExpression) {
            val definitionName = type.definition.name
                    if (definitionName[0].isLetter()) definitionName[0].lowercase() else "x"
        } else {
            "x"
        }
        while (context.scope.resolveName(candidate) != null) {
            candidate += '\''
        }
        forbiddenNames.add(candidate)
        return candidate
    }
}