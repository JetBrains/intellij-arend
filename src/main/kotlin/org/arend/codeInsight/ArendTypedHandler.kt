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
import com.intellij.util.text.CharArrayCharSequence
import org.arend.psi.ArendElementTypes.*
import org.arend.settings.ArendSettings
import org.arend.psi.ArendFile
import org.arend.psi.childOfType
import org.arend.psi.ext.ArendCompositeElementImpl


class ArendTypedHandler : TypedHandlerDelegate() {

    private fun changeCorrespondingElement(c: Char, project: Project, editor: Editor, file: PsiFile) {
        val parent = file.findElementAt(editor.selectionModel.selectionStart)
            ?.parent as? ArendCompositeElementImpl?

        val correspondingElementOffset = when (c) {
            '(' -> parent?.childOfType(RBRACE)?.textOffset
            '{' -> parent?.childOfType(RPAREN)?.textOffset
            ')' -> parent?.childOfType(LBRACE)?.textOffset
            else -> parent?.childOfType(LPAREN)?.textOffset
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

    override fun beforeSelectionRemoved(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (PARENTHESES_AND_BRACES.contains(c.toString())) {
            val selectedText = editor.selectionModel.selectedText
            if (PARENTHESES_AND_BRACES.contains(selectedText)) {
                changeCorrespondingElement(c, project, editor, file)
                return Result.CONTINUE
            }
        }
        return SelectionQuotingTypedHandler().beforeSelectionRemoved(c, project, editor, file)
    }

    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (file !is ArendFile) {
            return super.charTyped(c, project, editor, file)
        }
        if (PARENTHESES_AND_BRACES.contains(c.toString())) {
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

private val PARENTHESES_AND_BRACES = listOf("(", "{", ")", "}")
