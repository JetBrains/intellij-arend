package org.arend.codeInsight

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.util.text.CharArrayCharSequence
import org.arend.settings.ArendSettings
import org.arend.psi.ArendFile


class ArendTypedHandler : TypedHandlerDelegate() {
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

        // println("text[offset]=${text[offset]}, text[offset - 1]=${text[offset - 1]}")
        val atRBrace = offset < text.length && text[offset] == '}'
        if (atRBrace && c == '}' && text[offset - 1] == '}') {
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