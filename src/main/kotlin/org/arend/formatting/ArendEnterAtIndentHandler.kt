package org.arend.formatting

import com.intellij.codeInsight.editorActions.BackspaceHandler
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.injected.editor.EditorWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.openapi.util.Ref
import org.arend.psi.ArendFile

class ArendEnterAtIndentHandler : EnterHandlerDelegateAdapter() {
    override fun preprocessEnter(origFile: PsiFile, origEditor: Editor, caretOffset: Ref<Int>, caretAdvance: Ref<Int>, dataContext: DataContext, originalHandler: EditorActionHandler?): EnterHandlerDelegate.Result {
        var file = origFile
        var editor = origEditor

        if (editor is EditorWindow) {
            file = InjectedLanguageManager.getInstance(file.project).getTopLevelFile(file)
            editor = editor.delegate
        }
        if (file !is ArendFile) {
            return EnterHandlerDelegate.Result.Continue
        }

        return if (BackspaceHandler.isWhitespaceBeforeCaret(editor)) {
            EnterHandlerDelegate.Result.DefaultSkipIndent
        } else EnterHandlerDelegate.Result.Continue
    }

}