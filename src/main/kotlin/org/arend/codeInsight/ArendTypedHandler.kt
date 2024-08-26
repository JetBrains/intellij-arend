package org.arend.codeInsight

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.SelectionQuotingTypedHandler
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.text.CharArrayCharSequence
import org.arend.psi.ArendElementTypes.*
import org.arend.settings.ArendSettings
import org.arend.psi.ArendFile
import org.arend.psi.childOfType
import org.arend.psi.ext.ArendCompositeElement


class ArendTypedHandler : TypedHandlerDelegate() {

    private fun changeCorrespondingBracket(c: Char, project: Project, editor: Editor, file: PsiFile) {
        var element = file.findElementAt(editor.selectionModel.selectionStart)
        while (element !is ArendCompositeElement?) {
            element = element?.parent
        }
        if (element == null) {
            return
        }

        val correspondingElementOffset = when (c) {
            '(' -> element.childOfType(RBRACE)?.textOffset
            '{' -> element.childOfType(RPAREN)?.textOffset
            ')' -> element.childOfType(LBRACE)?.textOffset
            else -> element.childOfType(LPAREN)?.textOffset
        } ?: return

        val document = editor.document
        PsiDocumentManager.getInstance(project).commitDocument(document)

        document.replaceString(
            correspondingElementOffset,
            correspondingElementOffset + 1,
            when (c) {
                '(' -> ")"
                '{' -> "}"
                ')' -> "("
                else -> "{"
            }
        )
    }

    private fun addParens(project: Project, editor: Editor, file: PsiFile) {
        val element = file.findElementAt(editor.selectionModel.selectionStart) ?: return
        val document = editor.document
        PsiDocumentManager.getInstance(project).commitDocument(document)
        document.insertString(element.startOffset, "(")
        document.insertString(element.endOffset + 1, ")")
    }

    override fun beforeSelectionRemoved(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        val selectedText = editor.selectionModel.selectedText
        if (c == '(' && selectedText == "{?}") {
            addParens(project, editor, file)
            return Result.STOP
        } else if (BRACKETS.contains(c.toString())) {
            if (BRACKETS.contains(selectedText)) {
                changeCorrespondingBracket(c, project, editor, file)
                return Result.CONTINUE
            }
        }
        return SelectionQuotingTypedHandler().beforeSelectionRemoved(c, project, editor, file)
    }

    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (file !is ArendFile) {
            return super.charTyped(c, project, editor, file)
        }
        if (c == '{' || c == '(') {
            return Result.STOP // To prevent auto-formatting
        }

        val offset = editor.caretModel.offset
        val document = editor.document
        val text = document.charsSequence

        val atRBrace = offset < text.length && text[offset] == '}'
        if (atRBrace && c == '}' && offset > 2 && text[offset - 3] == '{' && text[offset - 2] == '?') {
            PsiDocumentManager.getInstance(project).commitDocument(document)
            document.deleteString(offset, offset + 1)
            return Result.STOP
        }

        if (c != '-') {
            return Result.CONTINUE
        }

        val style = service<ArendSettings>().matchingCommentStyle
        if (style == ArendSettings.MatchingCommentStyle.DO_NOTHING || style == ArendSettings.MatchingCommentStyle.INSERT_MINUS && !CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) {
            return Result.CONTINUE
        }

        PsiDocumentManager.getInstance(project).commitDocument(document)

        if (atRBrace && offset > 1 && text[offset - 2] == '{') {
            if (style == ArendSettings.MatchingCommentStyle.INSERT_MINUS) {
                document.insertString(offset, CharArrayCharSequence('-'))
            } else {
                document.deleteString(offset, offset + 1)
            }
        }

        return Result.CONTINUE
    }

    override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile) =
        if (charTyped == '\\') {
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
            Result.STOP
        } else {
            Result.CONTINUE
        }

}

private val BRACKETS = listOf("(", "{", ")", "}")
