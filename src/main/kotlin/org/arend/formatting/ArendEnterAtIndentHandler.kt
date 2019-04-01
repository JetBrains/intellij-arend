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
import org.arend.psi.ArendElementTypes
import org.arend.psi.ArendElementTypes.LINE_DOC_COMMENT_START
import org.arend.psi.ArendElementTypes.LINE_DOC_TEXT
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

        val charSeq = editor.document.charsSequence
        val currentOffset = editor.logicalPositionToOffset(editor.caretModel.logicalPosition)

        val startParenOffset = startingParenOffset(currentOffset, charSeq, origFile)
        if (startParenOffset != -1) {
            insertLineBreak(editor, startParenOffset, "")
            return EnterHandlerDelegate.Result.Stop
        }

        val comment = file.findElementAt(currentOffset)

        if (comment != null && (comment.node.elementType == ArendElementTypes.BLOCK_DOC_TEXT)) {
            val commentStart = comment.node.treePrev
            val relativeOffset = currentOffset - commentStart.startOffset
            val commentStartPortion = (commentStart.text + comment.text).substring(0, relativeOffset)
            val lastMatch = Regex("((\\n( )+-)|(\\{-))").findAll(commentStartPortion).lastOrNull()
            if (lastMatch != null) {
                val dashIndex = lastMatch.range.last + commentStart.startOffset
                insertLineBreak(editor, dashIndex, "- ")
                return EnterHandlerDelegate.Result.Stop
            }
        }

        if (comment != null && (comment.node.elementType == LINE_DOC_TEXT)) {
            val commentStart = comment.node.treePrev
            if (commentStart.elementType == LINE_DOC_COMMENT_START) {
                insertLineBreak(editor, commentStart.startOffset, "-- ")
                return EnterHandlerDelegate.Result.Stop
            }
        }

        return if (BackspaceHandler.isWhitespaceBeforeCaret(editor)) {
            EnterHandlerDelegate.Result.DefaultSkipIndent
        } else EnterHandlerDelegate.Result.Continue
    }

    companion object {
        private fun nextParen(caretOffset: Int, charSeq: CharSequence): Int {
            var offset = caretOffset
            while (offset < charSeq.length) {
                val c = charSeq[offset]
                if (c == ')' || c == '}')  return offset
                if (!(c == '\t' || c == ' ')) return -1
                offset++
            }
            return -1
        }

        fun startingParenOffset(caretOffset: Int, charSeq: CharSequence, file: PsiFile): Int {
            val parenOffset = nextParen(caretOffset, charSeq)
            if (parenOffset != -1 ) {
                val brace = file.findElementAt(parenOffset)
                val isBrace = charSeq[parenOffset] == '}'
                if (brace != null) {
                    val parent = brace.parent
                    val startBrace = when (parent) {
                        is ArendTuple -> if (parent.tupleExprList.isEmpty()) return -1 else parent.lparen
                        is ArendTypeTele -> if (isBrace) parent.lbrace else parent.lparen
                        else -> null
                    }
                    return startBrace?.textOffset ?: -1
                }
            }
            return -1
        }

        fun insertLineBreak(editor: Editor, startOffset: Int, suffix: String) {
            val document = editor.document
            val dashCol = editor.offsetToLogicalPosition(startOffset).column
            val spacing = "\n" + (" ".repeat(dashCol)) + suffix
            val caretOffset = editor.caretModel.offset
            document.insertString(caretOffset, spacing)
            editor.caretModel.moveToOffset(caretOffset + spacing.length)
        }
    }

}