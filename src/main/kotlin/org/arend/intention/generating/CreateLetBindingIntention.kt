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
import org.arend.intention.AbstractGenerateFunctionIntention
import org.arend.intention.BaseArendIntention
import org.arend.intention.ExtractExpressionToFunctionIntention
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.TCDefinition
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

class CreateLetBindingIntention : AbstractGenerateFunctionIntention() {
    // notation throughout this class:
    // extracted expression/range/etc -- entities that refer to something that will be replaced with a new binding
    // wrapped expression/range/etc -- entities that refer to something that will be wrapped in a let expression

    companion object {
        private const val COMMAND_GROUP_ID: @NonNls String = "__Arend__CreateLetBinding"

        private object MyListCellRenderer : DefaultListCellRenderer() {
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
        if (!BaseArendIntention.canModify(file)) return false
        val selection = editor.getSelectionWithoutErrors()?.takeIf { !it.isEmpty } ?: return false
        return file
                .findElementAt(editor.caretModel.offset)
                ?.parents(true)
                ?.firstOrNull { it.textRange.contains(selection) }
                ?.let(this::acceptableParents)
                ?.firstOrNull() != null
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

    override fun extractSelectionData(file: PsiFile, editor: Editor, project: Project): SelectionResult? =
            ExtractExpressionToFunctionIntention.doExtractSelectionData(file, editor, project)

    override fun performRefactoring(
            freeVariables: List<Pair<Binding, ParameterExplicitnessState>>,
            selection: SelectionResult,
            editor: Editor,
            project: Project
    ) {
        val wrappableOptions = collectWrappableOptions(selection.contextPsi, selection.rangeOfReplacement)

        if (wrappableOptions.size > 1) {
            val id = COMMAND_GROUP_ID + System.identityHashCode(selection)
            CommandProcessor.getInstance().currentCommandGroupId = id
            val optionRenderer = LetWrappingOptionEditorRenderer(editor, project, id)
            val popup = createPopup(wrappableOptions, optionRenderer, editor, freeVariables, selection)
            DaemonCodeAnalyzer.getInstance(project).disableUpdateByTimer(popup)
            Disposer.register(popup, optionRenderer)
            popup.showInBestPositionFor(editor)
        } else {
            runDocumentChanges(wrappableOptions.single(), editor, selection, freeVariables)
        }
    }

    private fun createPopup(
            options: List<WrappableOption>,
            optionRenderer: LetWrappingOptionEditorRenderer,
            editor: Editor,
            freeVariables: List<Pair<Binding, ParameterExplicitnessState>>,
            selection: SelectionResult): JBPopup = with(JBPopupFactory.getInstance().createPopupChooserBuilder(options)) {
        val elementToReplace = SmartPointerManager.createPointer(selection.contextPsi)
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
            val newSelection = selection.copy(contextPsi = elementToReplace.element!!)
            runDocumentChanges(it, editor, newSelection, freeVariables)
        }
        setRenderer(MyListCellRenderer)
        createPopup()
    }

    /**
     * All kinds of expressions that are allowed to be wrapped in a \let-expression.
     */
    private fun acceptableParents(rootPsi: PsiElement): Sequence<ArendExpr> = rootPsi
            .parentsOfType<ArendExpr>(true)
            .filter {
                it is ArendArgumentAppExpr ||
                        it is ArendCaseExpr ||
                        it is ArendLamExpr ||
                        it is ArendPiExpr ||
                        it is ArendSigmaExpr ||
                        it is ArendTupleExpr
            }

    private fun collectWrappableOptions(rootPsi: ArendCompositeElement, rangeOfReplacement: TextRange): List<WrappableOption> {
        val rangeMap : MutableMap<TextRange, Pair<ArendExpr, TextRange?>> = mutableMapOf()
        for (expression in acceptableParents(rootPsi)) {
            val letExpr = expression.parentOfType<ArendLetExpr>()?.takeIf { it.expr?.textRange == expression.textRange }
            val letRange = if (letExpr?.inKw != null) TextRange(letExpr.startOffset, letExpr.inKw!!.endOffset) else null
            val subRanges = collectInterestingSubranges(rootPsi, expression, rangeOfReplacement)
            for ((i, subExprRange) in subRanges.withIndex()) {
                if (i == subRanges.lastIndex) {
                    rangeMap[expression.textRange] = expression to letRange
                } else {
                    rangeMap[subExprRange] = expression to null
                }
            }
        }
        val wrappableOptions = mutableListOf<WrappableOption>()
        for ((range, exprWithLet) in rangeMap) {
            val (expr, let) = exprWithLet
            val basicText = rootPsi.containingFile.text.substring(range.startOffset, range.endOffset)
            val strippedText = stripText(basicText, expr, range)
            wrappableOptions.add(WrappableOption(SmartPointerManager.createPointer(expr), range, strippedText, let))
        }
        return wrappableOptions
    }

    private fun stripText(basicText: String, expr: ArendExpr, range: TextRange): String {
        return if (basicText.length > 50) {
            expr.accept(ShrinkAbstractVisitor(range), Unit)
        } else {
            basicText.replace('\n', ' ')
        }
    }

    private fun collectInterestingSubranges(rootPsi: ArendCompositeElement, binop: ArendExpr, rootSelection: TextRange): List<TextRange> {
        val parsed = parseBinOp(binop) ?: return listOf(rootPsi.textRange)
        val ranges = mutableListOf<TextRange>()
        forEachRange(parsed) { range, concrete ->
            if (concrete is Concrete.AppExpression && range.contains(rootSelection) && range != rootSelection) ranges.add(range)
            false
        }
        return ranges
    }

    @Suppress("RemoveExplicitTypeArguments", "MoveLambdaOutsideParentheses")
    private fun runDocumentChanges(wrappableOption: WrappableOption, editor: Editor, selection: SelectionResult, freeVariables: List<Pair<Binding, ParameterExplicitnessState>>) {
        val elementToWrap = wrappableOption.psi.element ?: return
        val project = elementToWrap.project
        val scope = elementToWrap.scope
        val actualFreeVariables = freeVariables.filter { scope.resolveName(it.first.name) == null }
        val representation = buildRepresentations(
                selection.contextPsi.parentOfType<TCDefinition>()!!,
                selection,
                actualFreeVariables,
                { "x" }) ?: return

        executeCommand(project, null, COMMAND_GROUP_ID) {
            runWriteAction {
                val (clauseInBindingOffset, invocationPlaceOffset) = performDocumentModifications(
                        elementToWrap,
                        wrappableOption.optionRange,
                        representation.first,
                        representation.second,
                        editor,
                        selection)
                        ?: return@runWriteAction
                editor.caretModel.moveToOffset(invocationPlaceOffset)
                invokeRenamer(editor, clauseInBindingOffset, project)
            }
        }
    }

    private fun performDocumentModifications(
            wrappedElement: ArendCompositeElement,
            rangeOfWrapped: TextRange,
            newFunctionCall: Concrete.Expression,
            clauseText: String,
            editor: Editor,
            selection: SelectionResult): Pair<Int, Int>? {
        val project = wrappedElement.project

        val (newWrappableElement, globalOffsetOfInvocation, insertedLength) =
                replaceExtractedExpression(wrappedElement, selection, editor, newFunctionCall)

        val newRangeOfWrapped = rangeOfWrapped.grown(insertedLength - selection.rangeOfReplacement.length)

        val newLetExpr = addToLetExpression(
                newWrappableElement,
                newRangeOfWrapped,
                clauseText,
                editor.document)

        val pointerToLet = SmartPointerManager.createPointer(newLetExpr)
        CodeStyleManager.getInstance(project).reformatText(newLetExpr.containingFile, newLetExpr.startOffset, newLetExpr.inKw!!.endOffset)
        val reparsedLet = pointerToLet.element ?: return null
        val identifierInClause = reparsedLet.letClauseList.lastOrNull()?.defIdentifier?.startOffset ?: -1

        val expressionStart = reparsedLet.expr?.startOffset ?: return null
        return identifierInClause to (expressionStart + globalOffsetOfInvocation - rangeOfWrapped.startOffset)
    }

    private fun replaceExtractedExpression(
            trackedElement: ArendCompositeElement,
            selection: SelectionResult,
            editor: Editor,
            newFunctionCall: Concrete.Expression
    ): Triple<ArendCompositeElement, Int, Int> {
        val project = trackedElement.project
        val pointerToWrapped = SmartPointerManager.createPointer(trackedElement)
        val replacedExpression = replaceExprSmart(
                editor.document,
                selection.contextPsi,
                selection.selectedConcrete,
                selection.rangeOfReplacement,
                null,
                newFunctionCall,
                newFunctionCall.toString(),
                false)
        val newInvocationOffset =
                selection.rangeOfReplacement.startOffset + if (replacedExpression.startsWith("(")) 1 else 0

        PsiDocumentManager.getInstance(project).commitDocument(editor.document)
        val trackedElementReparsed = pointerToWrapped.element ?: error("Cannot track wrapped element")
        return Triple(trackedElementReparsed, newInvocationOffset, replacedExpression.length)
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

    private fun insertEmptyLetExpression(
            rootExpression: ArendCompositeElement,
            rangeOfWrapped: TextRange,
            clause: String,
            document: Document): ArendLetExpr {
        val relativeRangeOfWrapped = rangeOfWrapped.shiftLeft(rootExpression.startOffset)
        val textOfWrapped = rootExpression.text.substring(relativeRangeOfWrapped.startOffset, relativeRangeOfWrapped.endOffset)
        val syntheticLetExpr = ArendPsiFactory(rootExpression.project).createLetExpression(clause, textOfWrapped)
        replaceExprSmart(
                document,
                rootExpression,
                null,
                rangeOfWrapped,
                syntheticLetExpr,
                null,
                syntheticLetExpr.text,
                false)
        val psiFile = PsiDocumentManager.getInstance(rootExpression.project).run {
            commitDocument(document)
            getPsiFile(document)
        }
        return psiFile?.findElementAt(rangeOfWrapped.startOffset + 1)?.parentOfType()!!
    }

    private fun ArendLetExpr.addNewClause(clause: String): ArendLetExpr {
        val letClauses = letClauseList
        when (letClauses.size) {
            0 -> error("There are no empty let expressions in Arend")
            else -> {
                val clauseTextList = letClauses.map { it?.text ?: "" } + listOf(clause)
                val exprText = expr?.text ?: ""
                @NlsSafe val newLetRepresentation =
                        "${getKw().text} ${clauseTextList.joinToString("") { "\n | $it" }} \n\\in $exprText"
                val newLetPsi = ArendPsiFactory(project).createExpression(newLetRepresentation)
                return this.replace(newLetPsi) as ArendLetExpr
            }
        }
    }

    private fun ArendLetExpr.getKw(): PsiElement =
            letKw ?: haveKw ?: letsKw ?: havesKw ?: error("At least one of the keywords should be provided")
}