@file:Suppress("UnstableApiUsage")

package org.arend.intention.generating

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parents
import com.intellij.psi.util.parentsOfType
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.arend.core.context.binding.Binding
import org.arend.intention.ExtractExpressionToFunctionIntention
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.refactoring.replaceExprSmart
import org.arend.resolving.util.parseBinOp
import org.arend.term.concrete.Concrete
import org.arend.util.ArendBundle
import org.arend.util.ParameterExplicitnessState
import org.arend.util.forEachRange
import org.jetbrains.annotations.NonNls
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JList
import javax.swing.ListSelectionModel

class CreateLetBindingIntention : ExtractExpressionToFunctionIntention() {

    companion object {
        private const val COMMAND_GROUP_ID: @NonNls String = "__Arend__GenerateLetBinding"

        private object TrimmingListCellRenderer : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
                val rendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                val expr: WrappableOption = value as WrappableOption
                text = expr.text
                return rendererComponent
            }
        }
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        editor ?: return false
        file ?: return false
        if (!super.isAvailable(project, editor, file)) {
            return false
        }
        val selection = editor.getSelectionWithoutErrors() ?: return false
        val rootElement = file.findElementAt(editor.caretModel.offset)?.parents(true)?.firstOrNull {it.textRange.contains(selection)} ?: return false
        return acceptableParents(rootElement).firstOrNull() != null
    }

    override fun getText(): String = ArendBundle.message("arend.create.let.binding")

    private data class WrappableOption(
            val psi: SmartPsiElementPointer<ArendExpr>,
            val optionRange: TextRange,
            val text: String,
            val parentLetExpression: TextRange?
    ) {
        override fun toString(): String = text
    }

    @Suppress("RemoveExplicitTypeArguments")
    override fun performRefactoring(freeVariables: List<Pair<Binding, ParameterExplicitnessState>>,
                                    selection: SelectionResult,
                                    editor: Editor,
                                    project: Project) {
        val wrappableOptions = collectWrappableOptions(selection.contextPsi, selection.rangeOfReplacement)

        val elementToReplace = SmartPointerManager.createPointer(selection.contextPsi)
        val id = COMMAND_GROUP_ID + System.identityHashCode(selection)
        CommandProcessor.getInstance().currentCommandGroupId = id
        val optionRenderer = LetWrappingOptionEditorRenderer(editor, project, id)

        val popup = createPopup(wrappableOptions, optionRenderer, project, editor, freeVariables, selection, elementToReplace)
        DaemonCodeAnalyzer.getInstance(project).disableUpdateByTimer(popup)
        Disposer.register(popup, optionRenderer)
        popup.showInBestPositionFor(editor)
    }

    private fun createPopup(
            options: List<WrappableOption>,
            optionRenderer: LetWrappingOptionEditorRenderer,
            project: Project,
            editor: Editor,
            freeVariables: List<Pair<Binding, ParameterExplicitnessState>>,
            selection: SelectionResult,
            elementToReplace: SmartPsiElementPointer<ArendCompositeElement>): JBPopup = with(JBPopupFactory.getInstance().createPopupChooserBuilder(options)) {
        setSelectedValue(options[0], true)
        setMovable(false)
        setResizable(false)
        setRequestFocus(true)
        setCancelOnClickOutside(true)
        setCancelOnWindowDeactivation(true)
        setAutoSelectIfEmpty(true)
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        setTitle(ArendBundle.message("arend.create.let.binding.expression.to.wrap"))
        setItemSelectedCallback {
            val option = it ?: return@setItemSelectedCallback
            optionRenderer.renderOption(option.optionRange.startOffset, option.parentLetExpression)
        }
        setItemChosenCallback {
            optionRenderer.cleanup()
            val newSelection = recreateSelection(selection, elementToReplace)
            runDocumentChanges(it, project, editor, newSelection, freeVariables)
        }
        setRenderer(TrimmingListCellRenderer)
        createPopup()
    }

    private fun recreateSelection(selection: SelectionResult, elementToReplace: SmartPsiElementPointer<ArendCompositeElement>): SelectionResult =
            selection.copy(contextPsi = elementToReplace.element!!)

    private fun acceptableParents(rootPsi: PsiElement): Sequence<ArendExpr> = rootPsi
            .parentsOfType<ArendExpr>(true)
            .filter { it is ArendArgumentAppExpr ||
                    it is ArendCaseExpr ||
                    it is ArendLamExpr ||
                    it is ArendPiExpr ||
                    it is ArendSigmaExpr ||
                    it is ArendTupleExpr
            }

    private fun collectWrappableOptions(rootPsi: ArendCompositeElement, rangeOfReplacement: TextRange): List<WrappableOption> {
        val wrappableExpressions = acceptableParents(rootPsi).flatMap { expr -> unwrapBinOp(rootPsi, expr, rangeOfReplacement).map(expr::to) }.toList()
        val parentLetExpressionRanges = wrappableExpressions.map { (expr, range) ->
            val let = expr.takeIf { it.textRange == range }?.parentOfType<ArendLetExpr>()?.takeIf { it.expr?.textRange == expr.textRange }
            if (let != null && let.inKw != null) {
                TextRange(let.startOffset, let.inKw!!.endOffset)
            } else {
                null
            }
        }

        return wrappableExpressions.mapIndexed { ind, (expr, range) ->
            val basicText = rootPsi.containingFile.text.substring(range.startOffset, range.endOffset)
            val strippedText = if (basicText.length > 50) {
                if (expr is ArendArgumentAppExpr) {
                    val arglist = expr.argumentList
                    ShrinkingAbstractVisitor(range).visitBinOpSequence(null, expr.atomFieldsAcc
                            ?: expr.longNameExpr!!, arglist, null)
                } else {
                    expr.accept(ShrinkingAbstractVisitor(range), Unit) ?: "INVALID"
                }
            } else {
                basicText.replace('\n', ' ')
            }
            WrappableOption(SmartPointerManager.createPointer(expr), range, strippedText, parentLetExpressionRanges[ind])
        }
    }

    private fun unwrapBinOp(rootPsi: ArendCompositeElement, binop: ArendExpr, rootSelection: TextRange): List<TextRange> {
        val parsed = parseBinOp(binop) ?: return listOf(rootPsi.textRange)
        val ranges = mutableListOf<TextRange>()
        forEachRange(parsed) { range, concrete ->
            if (concrete is Concrete.AppExpression && range.contains(rootSelection) && range != rootSelection) ranges.add(range); false
        }
        return ranges
    }

    private fun runDocumentChanges(wrappableOption: WrappableOption, project: Project, editor: Editor, selection: SelectionResult, freeVariables: List<Pair<Binding, ParameterExplicitnessState>>) {
        val selectedElement = wrappableOption.psi.element ?: return
        val scope = selectedElement.scope
        val necessaryFreeVariables = freeVariables.filter { scope.resolveName(it.first.name) == null }
        val representation =
                buildRepresentations(selection.contextPsi.parentOfType()!!, selection, "a", necessaryFreeVariables)
                        ?: return

        executeCommand(project, null, COMMAND_GROUP_ID) {
            runWriteAction {
                val identifiers = wrapInLet(selectedElement, wrappableOption.optionRange, representation.first, selection.rangeOfReplacement, representation.second, project, editor, selection.selectedConcrete, selection.contextPsi)
                        ?: return@runWriteAction
                editor.caretModel.moveToOffset(identifiers.second)
                invokeRenamer(editor, identifiers.first, project)
            }
        }
    }

    private fun wrapInLet(
            wrappedElement: ArendCompositeElement,
            wrappedRange: TextRange,
            newFunctionCall: Concrete.Expression,
            rangeOfReplacement: TextRange,
            newDefinitionTail: String,
            project: Project,
            editor: Editor,
            selectedConcrete: Concrete.Expression?,
            psiToExtract: ArendCompositeElement?): Pair<Int, Int>? {
        val shiftedIdentifierOffset = rangeOfReplacement.startOffset - wrappedElement.startOffset // offset of the new binding in the wrapped expression
        val pointer = SmartPointerManager.createPointer(wrappedElement)
        val replaced = replaceExprSmart(editor.document, psiToExtract ?: wrappedElement, selectedConcrete, rangeOfReplacement, null, newFunctionCall, newFunctionCall.toString(), false)
        val actualShiftedIdentifier = if (replaced.startsWith("(")) shiftedIdentifierOffset + 1 else shiftedIdentifierOffset
        PsiDocumentManager.getInstance(project).commitDocument(editor.document)
        val wrappedElementReparsed = pointer.element ?: return null

        val newWrappedRange = wrappedRange.grown(replaced.length - rangeOfReplacement.length)

        val newLetExpr = addToLetExpression(wrappedElementReparsed, newWrappedRange, newDefinitionTail, editor.document)

        val newLetPointer = SmartPointerManager.createPointer(newLetExpr)
        CodeStyleManager.getInstance(project).reformatText(newLetExpr.containingFile, newLetExpr.startOffset, newLetExpr.inKw!!.endOffset)
        val offsetOfClause = newLetPointer.element?.letClauseList?.lastOrNull()?.defIdentifier?.startOffset ?: -1


        val letExprPsi = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)?.findElementAt(offsetOfClause)?.parentOfType<ArendLetExpr>()
                ?: return null
        val expressionStart = letExprPsi.expr?.startOffset ?: return null
        return offsetOfClause to (expressionStart + actualShiftedIdentifier)
    }

    /**
     * Returns a modified let expression which is inserted to PSI tree
     */
    private fun addToLetExpression(rootExpression: ArendCompositeElement, rangeOfWrapping: TextRange, clause: String, document: Document): ArendLetExpr {
        val enclosingLet = rootExpression
                .parentOfType<ArendLetExpr>()
                ?.takeIf { it.expr?.textRange == rootExpression.textRange }
                ?: return insertEmptyLetExpression(rootExpression, rangeOfWrapping, clause, document)
        return enclosingLet.addNewClause(clause)
    }

    private fun insertEmptyLetExpression(rootExpression: ArendCompositeElement, rangeOfWrapping: TextRange, clause: String, document: Document): ArendLetExpr {
        val relativeRange = rangeOfWrapping.shiftLeft(rootExpression.startOffset)
        val subExpr = rootExpression.text.substring(relativeRange.startOffset, relativeRange.endOffset)
        val letExpr = ArendPsiFactory(rootExpression.project).createLetExpression(clause, subExpr)
        val offset = rangeOfWrapping.startOffset
        val documentManager = PsiDocumentManager.getInstance(rootExpression.project)
        replaceExprSmart(document, rootExpression, null, rangeOfWrapping, letExpr, null, letExpr.text, false)
        val psiFile = documentManager.run {
            commitDocument(document)
            getPsiFile(document)
        }
        return psiFile?.findElementAt(offset + 1)?.parentOfType()!!
    }

    private fun ArendLetExpr.addNewClause(clause: String): ArendLetExpr {
        val letClauses = letClauseList
        when (letClauses.size) {
            0 -> error("There is no empty let expressions in Arend")
            else -> {
                val texts = letClauses.map { it?.text ?: "" } + listOf(clause)
                val exprText = expr?.text ?: ""
                @NlsSafe val newLet = "${getKw().text} \n ${texts.joinToString("\n| ", "| ")} \n\\in $exprText"
                val factory = ArendPsiFactory(project)
                val newLetPsi = factory.createExpression(newLet)
                return this.replace(newLetPsi) as ArendLetExpr
            }
        }
    }

    private fun ArendLetExpr.getKw(): PsiElement =
            letKw ?: haveKw ?: letsKw ?: havesKw ?: error("At least one of the keywords should be provided")
}