package org.arend.intention.binOp

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.util.containers.headTailOrNull
import org.arend.psi.ext.ArendImplicitArgument
import org.arend.refactoring.rangeOfConcrete
import org.arend.term.concrete.Concrete
import org.arend.util.ArendBundle

abstract class BinOpSeqProcessor {
    fun run(project: Project, editor: Editor, initialBinOp: PsiElement, binOpSeq: Concrete.AppExpression) {
        val binOpSeqRange = rangeOfConcrete(binOpSeq)
        val caretHelper = CaretHelper(initialBinOp, binOpSeqRange, editor.document)
        val withParens = mapBinOp(binOpSeq, editor, caretHelper) ?: return
        val (withoutMarker, markerOffset) = caretHelper.removeCaretMarker(withParens)
        editor.document.replaceString(binOpSeqRange.startOffset, binOpSeqRange.endOffset, withoutMarker)
        if (markerOffset >= 0) {
            editor.caretModel.moveToOffset(binOpSeqRange.startOffset + markerOffset)
        }
        CodeStyleManager.getInstance(project).reformatText(initialBinOp.containingFile,
                binOpSeqRange.startOffset, binOpSeqRange.startOffset + withoutMarker.length)
    }

    fun mapBinOp(binOpApp: Concrete.AppExpression, editor: Editor, caretHelper: CaretHelper): String? {
        val result = StringBuilder()
        val implicitArgs = binOpApp.arguments.takeWhile { !it.isExplicit }
        val args = binOpApp.arguments.dropWhile { !it.isExplicit }
        if (args.any { !it.isExplicit }) {
            return nullAndShowError(ArendBundle.message("arend.expression.clarifyingParens.unexpectedUseOfImplicitArgs"), editor)
        }
        val (firstArg, restArgs) = args.headTailOrNull() ?: return nullAndShowError(editor)
        result.append(mapArgument(firstArg, binOpApp, editor, caretHelper) ?: return nullAndShowError(editor))
        val binOp = binOpApp.function
        result.append(whitespace(binOp, firstArg.expression, editor)).append(text(binOp, editor))
        if (caretHelper.shouldAddCaretMarker(binOp)) {
            result.append(CaretHelper.CARET_MARKER)
        }
        val argsAfterBinOp = implicitArgs + restArgs
        val previousExpressions = listOf(binOp) + argsAfterBinOp.map { it.expression }
        argsAfterBinOp.zip(previousExpressions).forEach { (arg, prev) ->
            val argWithParens = mapArgument(arg, binOpApp, editor, caretHelper) ?: return nullAndShowError(editor)
            result.append(whitespace(arg.expression, prev, editor)).append(argWithParens)
        }
        return result.toString()
    }

    protected abstract fun mapArgument(arg: Concrete.Argument,
                                       parentBinOp: Concrete.AppExpression,
                                       editor: Editor,
                                       caretHelper: CaretHelper): String?

    companion object {
        private fun nullAndShowError(editor: Editor): String? =
                nullAndShowError(ArendBundle.message("arend.expression.clarifyingParens.processingFailed"), editor)

        private fun nullAndShowError(message: String, editor: Editor): String? {
            HintManager.getInstance().showErrorHint(editor, message)
            return null
        }

        fun text(expression: Concrete.Expression, editor: Editor) =
                editor.document.getText(rangeOfConcrete(expression))

        fun implicitArgumentText(arg: Concrete.Argument, editor: Editor): String {
            val text = text(arg.expression, editor)
            return if (arg.expression.data is ArendImplicitArgument) text else "{$text}"
        }

        private fun whitespace(current: Concrete.Expression, previous: Concrete.Expression, editor: Editor): Char {
            val previousEnd = rangeOfConcrete(previous).endOffset
            val currentStart = rangeOfConcrete(current).startOffset
            val range = if (previousEnd < currentStart) TextRange(previousEnd, currentStart) else return ' '
            return if (editor.document.getText(range).contains('\n')) '\n' else ' '
        }
    }
}