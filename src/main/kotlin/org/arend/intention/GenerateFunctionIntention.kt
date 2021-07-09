package org.arend.intention

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.parents
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
import org.arend.psi.getSelectionWithoutErrors
import org.arend.psi.parentOfType
import org.arend.refactoring.rangeOfConcrete
import org.arend.refactoring.rename.ArendGlobalReferableRenameHandler
import org.arend.refactoring.replaceExprSmart
import org.arend.refactoring.tryCorrespondedSubExpr
import org.arend.resolving.ArendReferableConverter
import org.arend.term.prettyprint.PrettyPrinterConfigWithRenamer
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.error.local.GoalError
import org.arend.util.ArendBundle
import org.arend.util.FreeVariablesWithDependenciesCollector
import org.arend.util.ParameterExplicitnessState
import java.util.*

class GenerateFunctionIntention : BaseIntentionAction() {

    override fun getText(): String = ArendBundle.message("arend.generate.function")
    override fun getFamilyName() = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        editor ?: return false
        file ?: return false
        if (!canModify(file) || !BaseArendIntention.canModify(file)) {
            return false
        }
        val selection = editor.getSelectionWithoutErrors() ?: return false
        return !selection.isEmpty ||
                file.findElementAt(editor.caretModel.offset)?.parentOfType<ArendGoal>(false) != null
    }

    private data class SelectionResult(val expectedType : Expression, val replaceablePsi : ArendCompositeElement, val identifier : String?, val body : PsiElement?)

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        editor ?: return
        file ?: return
        val selection = editor.getSelectionWithoutErrors() ?: return
        val selectionResult = if (selection.isEmpty) {
            val goal = file.findElementAt(editor.caretModel.offset)?.parentOfType<ArendGoal>(false) ?: return
            val goalType = getGoalType(project, goal) ?: return
            SelectionResult(goalType, goal, goal.defIdentifier?.name, goal.expr)
        } else {
            val subexprResult = tryCorrespondedSubExpr(selection, file, project, editor) ?: return
            val enclosingRange = rangeOfConcrete(subexprResult.subConcrete)
            val enclosingPsi =
                subexprResult
                    .subPsi
                    .parents(true)
                    .lastOrNull { enclosingRange.contains(it.textRange) } as? ArendCompositeElement
                    ?: subexprResult.subPsi
            SelectionResult(subexprResult.subCore.type, enclosingPsi, null, enclosingPsi)
        }
        val freeVariables = FreeVariablesWithDependenciesCollector.collectFreeVariables(selectionResult.expectedType)
        performRefactoring(freeVariables, selectionResult, editor, project)
    }

    private fun getPrettyPrintConfig(context: ArendCompositeElement): PrettyPrinterConfig {
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
        freeVariables: List<Pair<Binding, ParameterExplicitnessState>>, selection : SelectionResult,
        editor: Editor, project: Project
    ) {
        val enclosingFunctionDefinition = selection.replaceablePsi.parentOfType<ArendFunctionalDefinition>() ?: return
        val (newFunctionCall, newFunctionDefinition) = buildRepresentations(
            selection,
            enclosingFunctionDefinition,
            freeVariables,
        ) ?: return

        val globalOffsetOfNewDefinition =
            modifyDocument(editor, newFunctionCall, selection.replaceablePsi, newFunctionDefinition, enclosingFunctionDefinition, project)

        invokeRenamer(editor, globalOffsetOfNewDefinition, project)
    }

    private fun buildRepresentations(
        selection: SelectionResult,
        functionDefinition: ArendFunctionalDefinition,
        freeVariables: List<Pair<Binding, ParameterExplicitnessState>>,
    ): Pair<String, String>? {
        val newFunctionName = selection.identifier ?: functionDefinition.defIdentifier?.name?.let { "$it-lemma" } ?: return null

        val ppconfig = getPrettyPrintConfig(selection.replaceablePsi)

        val goalTypeRepresentation = selection.expectedType.prettyPrint(ppconfig).toString()

        val explicitVariableNames = freeVariables.filter { it.second == ParameterExplicitnessState.EXPLICIT }
            .joinToString("") { " " + it.first.name }

        val parameters = freeVariables.joinToString("") { (binding, explicitness) ->
            " ${explicitness.openBrace}${binding.name} : ${binding.typeExpr.prettyPrint(ppconfig)}${explicitness.closingBrace}"
        }

        val actualGoalBody = selection.body?.text ?: "{?}"
        val newFunctionCall = "$newFunctionName$explicitVariableNames"
        val newFunctionDefinition = "\\func $newFunctionName$parameters : $goalTypeRepresentation => $actualGoalBody"
        return newFunctionCall to newFunctionDefinition
    }

    private fun modifyDocument(
        editor: Editor,
        newCall: String,
        replaceablePsi: ArendCompositeElement,
        newFunctionDefinition: String,
        oldFunction: PsiElement,
        project: Project
    ) : Int {
        val document = editor.document
        val startGoalOffset = replaceablePsi.startOffset
        val positionOfNewDefinition = oldFunction.endOffset - replaceablePsi.textLength + newCall.length + 4
        document.insertString(oldFunction.endOffset, "\n\n$newFunctionDefinition")
        val parenthesizedNewCall = replaceExprSmart(document, replaceablePsi, null, replaceablePsi.textRange, null, null, newCall, false)
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
