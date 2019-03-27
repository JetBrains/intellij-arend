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
import org.arend.psi.ArendTuple
import org.arend.psi.ArendTypeTele

class ArendEnterAtIndentHandler : EnterHandlerDelegateAdapter() {
    override fun preprocessEnter(origFile: PsiFile, origEditor: Editor, caretOffset: Ref<Int>, caretAdvance: Ref<Int>, dataContext: DataContext, originalHandler: EditorActionHandler?): EnterHandlerDelegate.Result {
        var file = origFile
        var editor = origEditor

        if (editor is EditorWindow) {
            file = InjectedLanguageManager.getInstance(file.project).getTopLevelFile(file)
            editor = editor.delegate
        }
        if (file !is ArendFile) return EnterHandlerDelegate.Result.Continue

        val startParenOffset = startingParenOffset(editor, origFile)
        if (startParenOffset != -1) {
            val startCol = editor.offsetToLogicalPosition(startParenOffset).column
            val spacing = "\n" + (" ".repeat(startCol))
            val document = editor.document
            document.insertString(caretOffset.get(), spacing)
            origEditor.caretModel.moveToOffset(caretOffset.get() + spacing.length)
            return EnterHandlerDelegate.Result.Stop
        }

        return if (BackspaceHandler.isWhitespaceBeforeCaret(editor)) {
            EnterHandlerDelegate.Result.DefaultSkipIndent
        } else EnterHandlerDelegate.Result.Continue
    }

    companion object {
        fun startingParenOffset(editor: Editor, file: PsiFile): Int {
            val charSeq = editor.document.charsSequence
            var offset = editor.logicalPositionToOffset(editor.caretModel.logicalPosition)

            fun isWhitespace(c : Char) = c == '\t' || c == ' '

            while (offset < charSeq.length) {
                val c = charSeq[offset]
                if (c == ')' || c == '}') {
                    val brace = file.findElementAt(offset)
                    val isBrace = c == '}'
                    if (brace != null) {
                        val parent = brace.parent
                        val startBrace = when (parent) {
                            is ArendTuple -> {
                                if (parent.tupleExprList.isEmpty()) return -1 else parent.lparen
                            }
                            is ArendTypeTele -> if (isBrace) parent.lbrace else parent.lparen
                            else -> null
                        }
                        return startBrace?.textOffset ?: -1
                    }
                }
                if (!isWhitespace(c)) return -1
                offset++
            }

            return -1
        }
    }

}