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
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentsOfType
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.arend.core.context.binding.Binding
import org.arend.intention.ExtractExpressionToFunctionIntention
import org.arend.psi.ArendAppExpr
import org.arend.psi.ArendLetExpr
import org.arend.psi.ArendPsiFactory
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.TCDefinition
import org.arend.refactoring.replaceExprSmart
import org.arend.term.concrete.Concrete
import org.arend.util.ArendBundle
import org.arend.util.ParameterExplicitnessState
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
                val text = expr.text
                val firstNewLinePos = text.indexOf('\n')
                var trimmedText = text.substring(0, if (firstNewLinePos != -1) firstNewLinePos else Math.min(100, text.length))
                if (trimmedText.length != text.length) trimmedText += " ..."
                setText(trimmedText)
                return rendererComponent
            }
        }
    }

    override fun getText(): String = ArendBundle.message("arend.create.let.binding")

    private data class WrappableOption(
            val psi: SmartPsiElementPointer<ArendCompositeElement>,
            val offset: Int,
            val text: String,
            val parentLetExpression: TextRange?
    )

    @Suppress("RemoveExplicitTypeArguments")
    override fun performRefactoring(freeVariables: List<Pair<Binding, ParameterExplicitnessState>>,
                                    selection: SelectionResult,
                                    editor: Editor,
                                    project: Project) {
        val wrappableOptions = collectWrappableOptions(selection.contextPsi)

        CommandProcessor.getInstance().currentCommandGroupId = COMMAND_GROUP_ID
        val optionRenderer = LetWrappingOptionEditorRenderer(editor, project, COMMAND_GROUP_ID)
        val representation =
                buildRepresentations(selection.contextPsi.parentOfType<TCDefinition>()!!, selection, "a", freeVariables)

        representation ?: return

        val popup = createPopup(wrappableOptions, optionRenderer, project, editor, representation, selection)
        DaemonCodeAnalyzer.getInstance(project).disableUpdateByTimer(popup)
        Disposer.register(popup, optionRenderer)
        popup.showInBestPositionFor(editor)
    }

    private fun createPopup(
            options: List<WrappableOption>,
            optionRenderer: LetWrappingOptionEditorRenderer,
            project: Project,
            editor: Editor,
            representation: Pair<Concrete.Expression, String>,
            selection: SelectionResult): JBPopup
    = with(JBPopupFactory.getInstance().createPopupChooserBuilder(options)) {
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
            optionRenderer.renderOption(option.offset, option.parentLetExpression)
        }
        setItemChosenCallback {
            optionRenderer.cleanup()
            runDocumentChanges(it, project, editor, selection, representation.first, representation.second)
        }
        setRenderer(TrimmingListCellRenderer)
        createPopup()
    }

    private fun collectWrappableOptions(rootPsi: ArendCompositeElement): List<WrappableOption> {
        val wrappableExpressions = rootPsi.parentsOfType<ArendAppExpr>().toList()
        val parentLetExpressionRanges = wrappableExpressions.map { expr ->
            val let = expr.parentOfType<ArendLetExpr>()?.takeIf { it.expr?.textRange == expr.textRange }
            if (let != null && let.inKw != null) {
                TextRange(let.startOffset, let.inKw!!.endOffset)
            } else {
                null
            }
        }
        return wrappableExpressions.mapIndexed { ind, it -> WrappableOption(SmartPointerManager.createPointer(it), it.textOffset, it.text, parentLetExpressionRanges[ind]) }
    }

    private fun runDocumentChanges(wrappableOption: WrappableOption, project: Project, editor: Editor, selection: SelectionResult, callConcrete : Concrete.Expression, callText : String) {
        val selectedElement = wrappableOption.psi.element ?: return
        executeCommand(project, null, COMMAND_GROUP_ID) {
            val identifiers = runWriteAction {
                wrapInLet(selectedElement, callConcrete, selection.rangeOfReplacement, callText, project, editor, selection.selectedConcrete)
            } ?: return@executeCommand
            editor.caretModel.moveToOffset(identifiers.second)
            invokeRenamer(editor, identifiers.first, project)
        }
    }

    private fun wrapInLet(
            wrappedElement: ArendCompositeElement,
            newFunctionCall: Concrete.Expression,
            rangeOfReplacement: TextRange,
            newDefinitionTail: String,
            project: Project,
            editor: Editor,
            selectedConcrete: Concrete.Expression?): Pair<Int, Int>? {
        val shiftedIdentifierOffset = rangeOfReplacement.startOffset - wrappedElement.startOffset // offset of the new binding in the wrapped expression
        val pointer = SmartPointerManager.createPointer(wrappedElement)

        replaceExprSmart(editor.document, wrappedElement, selectedConcrete, rangeOfReplacement, null, newFunctionCall, newFunctionCall.toString(), false)
        PsiDocumentManager.getInstance(project).commitDocument(editor.document)
        val wrappedElementReparsed = pointer.element ?: return null

        val newLetExpr = addToLetExpression(wrappedElementReparsed, newDefinitionTail, editor.document)

        val newLetPointer = SmartPointerManager.createPointer(newLetExpr)
        CodeStyleManager.getInstance(project).reformatText(newLetExpr.containingFile, newLetExpr.startOffset, newLetExpr.inKw!!.endOffset)
        val offsetOfClause = newLetPointer.element?.letClauseList?.lastOrNull()?.defIdentifier?.startOffset ?: -1


        val letExprPsi = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)?.findElementAt(offsetOfClause)?.parentOfType<ArendLetExpr>()
                ?: return null
        val expressionStart = letExprPsi.expr?.startOffset ?: return null
        return offsetOfClause to (expressionStart + shiftedIdentifierOffset)
    }

    /**
     * Returns a modified let expression which is inserted to PSI tree
     */
    private fun addToLetExpression(rootExpression: ArendCompositeElement, clause: String, document: Document): ArendLetExpr {
        val enclosingLet = rootExpression
                .parentOfType<ArendLetExpr>()
                ?.takeIf { it.expr?.textRange == rootExpression.textRange }
                ?: return insertEmptyLetExpression(rootExpression, clause, document)
        return enclosingLet.addNewClause(clause)
    }

    private fun insertEmptyLetExpression(rootExpression: ArendCompositeElement, clause: String, document: Document): ArendLetExpr {
        val letExpr = ArendPsiFactory(rootExpression.project).createLetExpression(clause, rootExpression.text)
        val offset = rootExpression.textOffset
        val documentManager = PsiDocumentManager.getInstance(rootExpression.project)
        replaceExprSmart(document, rootExpression, null, rootExpression.textRange, letExpr, null, letExpr.text, false)
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