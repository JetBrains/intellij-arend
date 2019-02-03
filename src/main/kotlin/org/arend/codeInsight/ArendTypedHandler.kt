package org.arend.codeInsight

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.util.text.CharArrayCharSequence
import org.arend.editor.ArendSmartKeysOptions
import org.arend.psi.ArendFile


class ArendTypedHandler : TypedHandlerDelegate() {
    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (file !is ArendFile) {
            return super.charTyped(c, project, editor, file)
        }
        if (c == '{') {
            return Result.STOP // To prevent auto-formatting
        }
        if (c != '-') {
            return Result.CONTINUE
        }
        val style = ArendSmartKeysOptions.getInstance().matchingCommentStyle
        if (style == ArendSmartKeysOptions.MatchingCommentStyle.DO_NOTHING || style == ArendSmartKeysOptions.MatchingCommentStyle.INSERT_MINUS && !CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) {
            return Result.CONTINUE
        }

        TransactionGuard.getInstance().submitTransactionAndWait {
            PsiDocumentManager.getInstance(project).commitAllDocuments()

            val offset = editor.caretModel.offset
            val text = editor.document.charsSequence
            if (offset > 1 && text[offset - 2] == '{' && offset < text.length && text[offset] == '}') {
                if (style == ArendSmartKeysOptions.MatchingCommentStyle.INSERT_MINUS) {
                    editor.document.insertString(offset, CharArrayCharSequence('-'))
                } else {
                    editor.document.deleteString(offset, offset + 1)
                }
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