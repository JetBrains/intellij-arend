package org.arend.intention

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.arend.core.context.binding.Binding
import org.arend.core.expr.Expression
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.prettyprinting.PrettyPrinterFlag
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.ConvertingScope
import org.arend.psi.ArendDefFunction
import org.arend.psi.ArendGoal
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendFunctionalDefinition
import org.arend.psi.parentOfType
import org.arend.refactoring.rename.ArendGlobalReferableRenameHandler
import org.arend.refactoring.replaceExprSmart
import org.arend.resolving.ArendReferableConverter
import org.arend.term.prettyprint.PrettyPrinterConfigWithRenamer
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.error.local.GoalError
import org.arend.util.ArendBundle
import org.arend.util.FreeVariablesWithDependenciesCollector
import org.arend.util.ParameterExplicitnessState
import java.util.*

class GenerateFunctionIntention : BaseArendIntention(ArendBundle.message("arend.generate.function")) {
    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean =
        element.parentOfType<ArendGoal>(false) != null

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        editor ?: return
        val goal = element.parentOfType<ArendGoal>(false) ?: return
        val expectedGoalType = getGoalType(project, goal) ?: return
        val freeVariables = FreeVariablesWithDependenciesCollector.collectFreeVariables(expectedGoalType)
        performRefactoring(freeVariables, goal, editor, expectedGoalType, project)
    }

    private fun getPrettyPrintConfig(context : ArendCompositeElement) : PrettyPrinterConfig {
        val scope = context.scope.let(CachingScope::make)
        return object : PrettyPrinterConfigWithRenamer(scope?.let { CachingScope.make(ConvertingScope(ArendReferableConverter, it)) }) {
            override fun getExpressionFlags(): EnumSet<PrettyPrinterFlag> = EnumSet.noneOf(PrettyPrinterFlag::class.java)
        }
    }

    private fun getGoalType(project: Project, goal: ArendGoal): Expression? {
        val errorService = project.service<ErrorService>()
        val arendError = errorService.errors[goal.containingFile]?.firstOrNull { it.cause == goal } ?: return null
        return (arendError.error as? GoalError)?.expectedType
    }

    private fun performRefactoring(
        freeVariables: List<Pair<Binding, ParameterExplicitnessState>>, goal: ArendCompositeElement,
        editor: Editor, goalType: Expression, project: Project
    ) {
        val enclosingFunctionDefinition = goal.parentOfType<ArendFunctionalDefinition>() ?: return
        val (newFunctionCall, newFunctionDefinition) = buildRepresentations(goal,
            enclosingFunctionDefinition,
            freeVariables,
            goalType
        ) ?: return

        val globalOffsetOfNewDefinition =
            modifyDocument(editor, newFunctionCall, goal, newFunctionDefinition, enclosingFunctionDefinition, project)

        invokeRenamer(editor, globalOffsetOfNewDefinition, project)
    }

    private fun buildRepresentations(
        context: ArendCompositeElement,
        functionDefinition: ArendFunctionalDefinition,
        freeVariables: List<Pair<Binding, ParameterExplicitnessState>>,
        goalType: Expression
    ): Pair<String, String>? {
        val enclosingFunctionName = functionDefinition.defIdentifier?.name ?: return null

        val ppconfig = getPrettyPrintConfig(context)

        val goalTypeRepresentation = goalType.prettyPrint(ppconfig).toString()

        val explicitVariableNames = freeVariables.filter { it.second == ParameterExplicitnessState.EXPLICIT }
            .joinToString("") { " " + it.first.name }

        val parameters = freeVariables.joinToString("") { (binding, explicitness) ->
            " ${explicitness.openBrace}${binding.name} : ${binding.typeExpr.prettyPrint(ppconfig)}${explicitness.closingBrace}"
        }

        val newFunctionName = "$enclosingFunctionName-lemma"
        val newFunctionCall = "$newFunctionName$explicitVariableNames"
        val newFunctionDefinition = "\\func $newFunctionName$parameters : $goalTypeRepresentation => {?}"
        return newFunctionCall to newFunctionDefinition
    }

    private fun modifyDocument(
        editor: Editor,
        newCall: String,
        goal: ArendCompositeElement,
        newFunctionDefinition: String,
        oldFunction: PsiElement,
        project: Project
    ) : Int {
        val document = editor.document
        val startGoalOffset = goal.startOffset
        val positionOfNewDefinition = oldFunction.endOffset - goal.textLength + newCall.length + 4
        document.insertString(oldFunction.endOffset, "\n\n$newFunctionDefinition")
        val parenthesizedNewCall = replaceExprSmart(document, goal, null, goal.textRange, null, null, newCall, false)
        PsiDocumentManager.getInstance(project).commitDocument(document)
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return positionOfNewDefinition
        val callElementPointer =
            psiFile.findElementAt(startGoalOffset + 1)!!.let(SmartPointerManager::createPointer)
        val newDefinitionPointer =
            psiFile.findElementAt(positionOfNewDefinition)!!.let(SmartPointerManager::createPointer)
        CodeStyleManager.getInstance(project).reformatText(
            psiFile,
            listOf(
                TextRange(startGoalOffset, startGoalOffset + parenthesizedNewCall.length),
                TextRange(positionOfNewDefinition - 2, positionOfNewDefinition + newFunctionDefinition.length)
            )
        )
        editor.caretModel.moveToOffset(callElementPointer.element!!.startOffset)
        return newDefinitionPointer.element!!.startOffset
    }

    private fun invokeRenamer(editor: Editor, functionOffset: Int, project: Project) {
        val newFunctionDefinition =
            PsiDocumentManager.getInstance(project).getPsiFile(editor.document)?.findElementAt(functionOffset)
                ?.parentOfType<ArendDefFunction>() ?: return
        ArendGlobalReferableRenameHandler().doRename(newFunctionDefinition, editor, null)
    }
}
