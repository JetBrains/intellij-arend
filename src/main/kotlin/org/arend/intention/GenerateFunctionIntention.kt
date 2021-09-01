package org.arend.intention

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parents
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.SmartList
import org.arend.core.context.binding.Binding
import org.arend.core.expr.Expression
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.extImpl.definitionRenamer.CachingDefinitionRenamer
import org.arend.extImpl.definitionRenamer.ScopeDefinitionRenamer
import org.arend.inspection.mayBeUnwrappedFromParentheses
import org.arend.inspection.unwrapTuple
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.ConvertingScope
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendFunctionalDefinition
import org.arend.psi.ext.TCDefinition
import org.arend.refactoring.rangeOfConcrete
import org.arend.refactoring.rename.ArendGlobalReferableRenameHandler
import org.arend.refactoring.tryCorrespondedSubExpr
import org.arend.resolving.ArendReferableConverter
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.MinimizedRepresentation
import org.arend.term.prettyprint.ToAbstractVisitor
import org.arend.typechecking.PsiInstanceProviderSet
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.error.local.GoalError
import org.arend.util.ArendBundle
import org.arend.util.FreeVariablesWithDependenciesCollector
import org.arend.util.ParameterExplicitnessState
import kotlin.math.exp

class GenerateFunctionIntention : BaseIntentionAction() {
    companion object {
        val log = logger<GenerateFunctionIntention>()
    }

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

    private data class SelectionResult(
        val expectedType: Expression?,
        val contextPsi: ArendCompositeElement,
        val rangeOfReplacement: TextRange,
        val identifier: String?,
        val body: Expression?
    )

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        editor ?: return
        file ?: return
        val selection = editor.getSelectionWithoutErrors() ?: return
        val selectionResult = if (selection.isEmpty) {
            extractGoalData(file, editor, project)
        } else {
            extractSelectionData(file, editor, project, selection)
        } ?: return
        val expressions = listOfNotNull(selectionResult.expectedType, selectionResult.body)
        val freeVariables = FreeVariablesWithDependenciesCollector.collectFreeVariables(expressions)
        performRefactoring(freeVariables, selectionResult, editor, project)
    }

    private fun extractGoalData(file : PsiFile, editor: Editor, project: Project): SelectionResult? {
        val goal = file.findElementAt(editor.caretModel.offset)?.parentOfType<ArendGoal>(false) ?: return null
        val errorService = project.service<ErrorService>()
        val arendError = errorService.errors[goal.containingFile]?.firstOrNull { it.cause == goal }?.error as? GoalError
            ?: return null
        val goalType = (arendError as? GoalError)?.expectedType
        val goalExpr = goal.expr?.let {
            tryCorrespondedSubExpr(it.textRange, file, project, editor)
        }?.subCore
        return SelectionResult(goalType, goal, goal.textRange, goal.defIdentifier?.name, goalExpr)
    }

    private fun extractSelectionData(file : PsiFile, editor: Editor, project: Project, range : TextRange) : SelectionResult? {
        val subexprResult = tryCorrespondedSubExpr(range, file, project, editor) ?: return null
        val enclosingRange = rangeOfConcrete(subexprResult.subConcrete)
        val enclosingPsi =
            subexprResult
                .subPsi
                .parents(true)
                .filterIsInstance<ArendExpr>()
                .lastOrNull { enclosingRange.contains(it.textRange) }
                ?: subexprResult.subPsi
        return SelectionResult(subexprResult.subCore.type, enclosingPsi, enclosingRange, null, subexprResult.subCore)
    }

    private fun performRefactoring(
        freeVariables: List<Pair<Binding, ParameterExplicitnessState>>, selection : SelectionResult,
        editor: Editor, project: Project
    ) {
        val enclosingFunctionDefinition = selection.contextPsi.parentOfType<ArendFunctionalDefinition>() ?: return
        val enclosingDefinitionReferable = selection.contextPsi.parentOfType<TCDefinition>()!!
        val (newFunctionCall, newFunctionDefinition) = buildRepresentations(
                enclosingDefinitionReferable, selection,
                enclosingFunctionDefinition,
                freeVariables,
        ) ?: return

        val globalOffsetOfNewDefinition =
                modifyDocument(editor, newFunctionCall, selection.rangeOfReplacement, newFunctionDefinition, enclosingFunctionDefinition, project)

        invokeRenamer(editor, globalOffsetOfNewDefinition, project)
    }

    private fun buildRepresentations(
            enclosingDefinitionReferable: TCDefinition,
            selection: SelectionResult,
            functionDefinition: ArendFunctionalDefinition,
            freeVariables: List<Pair<Binding, ParameterExplicitnessState>>,
    ): Pair<String, String>? {
        val newFunctionName = selection.identifier ?: functionDefinition.defIdentifier?.name?.let { "$it-lemma" }
        ?: return null

        val prettyPrinter: (Expression, Boolean) -> Concrete.Expression = run {
            val ip = PsiInstanceProviderSet().get(ArendReferableConverter.toDataLocatedReferable(enclosingDefinitionReferable)!!)
            val renamer = CachingDefinitionRenamer(ScopeDefinitionRenamer(selection.contextPsi.parentOfType<ArendStatement>()!!.scope.let { CachingScope.make(ConvertingScope(ArendReferableConverter, it)) }));

            { expr, useReturnType ->
                try {
                    MinimizedRepresentation.generateMinimizedRepresentation(expr, ip, renamer, useReturnType)
                } catch (e: Exception) {
                    log.error(e)
                    ToAbstractVisitor.convert(expr, object : PrettyPrinterConfig {
                        override fun getDefinitionRenamer() = renamer
                    })
                }
            }
        }

        val explicitVariableNames = freeVariables.filter { it.second == ParameterExplicitnessState.EXPLICIT }
                .joinToString("") { " " + it.first.name }

        val parameters = freeVariables.collapseTelescopes().joinToString("") { (bindings, explicitness) ->
            " ${explicitness.openingBrace}${bindings.joinToString(" ") {it.name}} : ${prettyPrinter(bindings.first().typeExpr, false)}${explicitness.closingBrace}"
        }

        val actualBody = selection.body?.let { prettyPrinter(it, true) } ?: "{?}"
        val newFunctionCall = "$newFunctionName$explicitVariableNames"
        val newFunctionDefinitionType = if (selection.expectedType != null) " : ${prettyPrinter(selection.expectedType, false)}" else ""
        val newFunctionDefinition = "\\func $newFunctionName$parameters$newFunctionDefinitionType => $actualBody"
        return newFunctionCall to newFunctionDefinition
    }

    private fun List<Pair<Binding, ParameterExplicitnessState>>.collapseTelescopes() : List<Pair<List<Binding>, ParameterExplicitnessState>> = fold(mutableListOf<Pair<MutableList<Binding>, ParameterExplicitnessState>>()) { collector, (binding, explicitness) ->
       if (collector.isEmpty() || collector.last().second == ParameterExplicitnessState.IMPLICIT) {
           collector.add(SmartList(binding) to explicitness)
       } else {
           val lastEntry = collector.last()
           if (lastEntry.first.first().type == binding.type) {
               lastEntry.first.add(binding)
           } else {
               collector.add(SmartList(binding) to explicitness)
           }
       }
        collector
    }

    private fun modifyDocument(
        editor: Editor,
        newCallRepresentation: String,
        rangeOfReplacement: TextRange,
        newFunctionDefinition: String,
        oldFunction: PsiElement,
        project: Project
    ) : Int {
        val document = editor.document
        val positionOfNewDefinition = oldFunction.endOffset - rangeOfReplacement.length + newCallRepresentation.length + 4 // 4 is for two parens and newlines
        document.insertString(oldFunction.endOffset, "\n\n$newFunctionDefinition")
        document.replaceString(rangeOfReplacement.startOffset, rangeOfReplacement.endOffset, "($newCallRepresentation)")
        PsiDocumentManager.getInstance(project).commitDocument(document)
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return positionOfNewDefinition
        val pointerToNewDefinition = psiFile.findElementAt(positionOfNewDefinition)!!.let(SmartPointerManager::createPointer)
        val newPositionOfCall = unwrapParentheses(psiFile, document, rangeOfReplacement)
        CodeStyleManager.getInstance(project).reformatText(
                psiFile,
                listOf(TextRange(newPositionOfCall, newPositionOfCall + newCallRepresentation.length),
                        TextRange(positionOfNewDefinition, positionOfNewDefinition + newFunctionDefinition.length))
        )
        editor.caretModel.moveToOffset(newPositionOfCall)
        return pointerToNewDefinition.element!!.startOffset
    }

    private fun unwrapParentheses(psiFile: PsiFile, document: Document, rangeOfReplacement: TextRange): Int {
        val newCallElement = psiFile.findElementAt(rangeOfReplacement.startOffset)?.parentOfType<ArendTuple>(true)
                ?: return rangeOfReplacement.startOffset + 1
        return if (mayBeUnwrappedFromParentheses(newCallElement)) {
            unwrapTuple(newCallElement, psiFile)
            PsiDocumentManager.getInstance(psiFile.project).commitDocument(document)
            rangeOfReplacement.startOffset
        } else {
            rangeOfReplacement.startOffset + 1
        }
    }

    private fun invokeRenamer(editor: Editor, functionOffset: Int, project: Project) {
        val newFunctionDefinition =
            PsiDocumentManager.getInstance(project).getPsiFile(editor.document)?.findElementAt(functionOffset)
                ?.parentOfType<ArendDefFunction>() ?: return
        ArendGlobalReferableRenameHandler().doRename(newFunctionDefinition, editor, null)
    }
}
