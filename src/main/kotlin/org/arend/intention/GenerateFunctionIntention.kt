package org.arend.intention

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.arend.core.context.binding.Binding
import org.arend.core.expr.Expression
import org.arend.psi.ArendDefFunction
import org.arend.psi.ArendGoal
import org.arend.psi.ext.ArendFunctionalDefinition
import org.arend.psi.parentOfType
import org.arend.refactoring.rename.ArendGlobalReferableRenameHandler
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.error.local.GoalError
import org.arend.util.ArendBundle
import org.arend.util.FreeVariablesWithDependenciesCollector
import org.arend.util.ParameterExplicitnessState

class GenerateFunctionIntention : BaseArendIntention(ArendBundle.message("arend.generate.function")) {
    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean =
        element.parentOfType<ArendGoal>(false) != null

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        editor ?: return
        val goal = element.parentOfType<ArendGoal>(false) ?: return
        val expectedGoalType = getGoalType(project, goal) ?: return
        val freeVariables = FreeVariablesWithDependenciesCollector.collectFreeVariables(expectedGoalType)
        performRefactoring(freeVariables, goal, editor, expectedGoalType.toString(), project)
    }

    private fun getGoalType(project: Project, goal: ArendGoal): Expression? {
        val errorService = project.service<ErrorService>()
        val arendError = errorService.errors[goal.containingFile]?.firstOrNull { it.cause == goal } ?: return null
        return (arendError.error as? GoalError)?.expectedType
    }

    private fun performRefactoring(
        freeVariables: List<Pair<Binding, ParameterExplicitnessState>>, goal: PsiElement,
        editor: Editor, goalTypeRepresentation: String, project: Project
    ) {
        val enclosingFunctionDefinition = goal.parentOfType<ArendFunctionalDefinition>() ?: return
        val (newFunctionCall, newFunctionDefinition) = buildRepresentations(
            enclosingFunctionDefinition,
            freeVariables,
            goalTypeRepresentation
        ) ?: return

        val newFunctionDefinitionOffset =
            enclosingFunctionDefinition.endOffset - goal.textLength + newFunctionCall.length + "\n\\func".length

        modifyDocument(editor, newFunctionCall, goal, newFunctionDefinition, enclosingFunctionDefinition, project)

        invokeRenamer(editor, newFunctionDefinitionOffset, project)
    }

    private fun buildRepresentations(
        functionDefinition: ArendFunctionalDefinition,
        freeVariables: List<Pair<Binding, ParameterExplicitnessState>>,
        goalTypeRepresentation: String
    ): Pair<String, String>? {
        val enclosingFunctionName = functionDefinition.defIdentifier?.name ?: return null

        val explicitVariableNames = freeVariables.filter { it.second == ParameterExplicitnessState.EXPLICIT }
            .joinToString(" ") { it.first.name }

        val parameters = freeVariables.joinToString(" ") { (binding, explicitness) ->
            "${explicitness.openBrace}${binding.name} : ${binding.typeExpr}${explicitness.closingBrace}"
        }

        val newFunctionName = "$enclosingFunctionName-lemma"
        val newFunctionCall = "$newFunctionName $explicitVariableNames"
        val newFunctionDefinition = "\\func $newFunctionName $parameters : $goalTypeRepresentation => {?}"
        return newFunctionCall to newFunctionDefinition
    }

    private fun modifyDocument(
        editor: Editor,
        newCall: String,
        goal: PsiElement,
        newFunctionDefinition: String,
        oldFunction: PsiElement,
        project: Project
    ) {
        val document = editor.document
        document.insertString(oldFunction.endOffset, "\n$newFunctionDefinition")
        document.replaceString(goal.startOffset, goal.endOffset, newCall)
        editor.caretModel.moveToOffset(goal.startOffset)
        PsiDocumentManager.getInstance(project).apply {
            doPostponedOperationsAndUnblockDocument(document)
            commitDocument(document)
        }
    }

    private fun invokeRenamer(editor: Editor, functionOffset: Int, project: Project) {
        val newFunctionDefinition =
            PsiDocumentManager.getInstance(project).getPsiFile(editor.document)?.findElementAt(functionOffset)
                ?.parentOfType<ArendDefFunction>() ?: return
        ArendGlobalReferableRenameHandler().doRename(newFunctionDefinition, editor, null)
    }
}
