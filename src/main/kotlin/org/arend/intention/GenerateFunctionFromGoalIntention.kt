package org.arend.intention

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import org.arend.core.context.binding.TypedBinding
import org.arend.core.expr.DefCallExpression
import org.arend.core.expr.Expression
import org.arend.core.expr.PiExpression
import org.arend.psi.ArendArgumentAppExpr
import org.arend.psi.ArendGoal
import org.arend.psi.ext.ArendCompositeElement
import org.arend.refactoring.tryCorrespondedSubExpr
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.error.local.GoalError
import org.arend.util.ArendBundle
import org.arend.util.ParameterExplicitnessState

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
        var currentGoalType : Expression? = goalType
        if (appParent != null && goalType is PiExpression) {
            val forbiddenNames = mutableSetOf<String>()
            val typecheckedUserArguments = appParent.argumentList.map { tryCorrespondedSubExpr(it.textRange, file, project, editor) to it.isExplicit }
            for ((argResult, explicitness) in typecheckedUserArguments) {
                currentGoalType as PiExpression
                val expectedType = currentGoalType.parameters.typeExpr
                val explicitnessState = if (explicitness) ParameterExplicitnessState.EXPLICIT else ParameterExplicitnessState.IMPLICIT
                if (argResult == null) {
                    additionalArguments.add(TypedBinding("_", expectedType) to explicitnessState)
                } else {
                    additionalArguments.add(TypedBinding(suggestParameterName(forbiddenNames, goal, expectedType), expectedType) to explicitnessState)
                }
                currentGoalType = currentGoalType.codomain
            }
        }
        val goalExpr = goal.expr?.let {
            tryCorrespondedSubExpr(it.textRange, file, project, editor)
        }?.subCore
        return SelectionResult(currentGoalType, goal, goal.textRange, null, goal.defIdentifier?.name, goalExpr, additionalArguments)
    }

    private fun suggestParameterName(forbiddenNames: MutableSet<String>, context : ArendCompositeElement, type : Expression) : String {
        var candidate = if (type is DefCallExpression) {
            val definitionName = type.definition.name
                    if (definitionName[0].isLetter()) definitionName[0].lowercase() else "x"
        } else {
            "x"
        }
        while(context.scope.resolveName(candidate) != null) {
            candidate += '\''
        }
        forbiddenNames.add(candidate)
        return candidate
    }
}