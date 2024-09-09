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
import com.intellij.psi.util.elementType
import com.intellij.psi.util.endOffset
import org.arend.documentation.AREND_DOC_COMMENT_TABS_SIZE
import org.arend.documentation.CONTEXT_ELEMENTS
import org.arend.parser.ParserMixin.*
import org.arend.psi.ArendFile
import org.arend.psi.ext.ArendTuple
import org.arend.psi.ext.ArendTypeTele
import org.arend.psi.nextElement
import org.arend.psi.prevElement

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

        if (comment != null && comment.node.elementType == DOC_COMMENT) {
            val commentStart = comment.node.treePrev
            val relativeOffset = currentOffset - commentStart.startOffset
            val commentStartPortion = (commentStart.text + comment.text).substring(0, relativeOffset)
            val lastMatch = Regex("((\\n( )+-)|(\\{-))").findAll(commentStartPortion).lastOrNull()
            if (lastMatch != null) {
                val dashIndex = lastMatch.range.last + commentStart.startOffset
                insertLineBreak(editor, dashIndex, "- ")
                return EnterHandlerDelegate.Result.Stop
            }
        } else if (comment?.elementType == DOC_NEWLINE &&
                    file.findElementAt(currentOffset - 1)?.elementType == DOC_START) {
            val document = editor.document
            document.insertString(currentOffset, "\n - ")
            if (!hasDocEnd(currentOffset + 1, file) && file.findElementAt(currentOffset + 1)?.elementType == DOC_TEXT) {
                document.insertString(currentOffset + 4, "\n -}")
            }
            editor.caretModel.moveToOffset(currentOffset + 4)
            return EnterHandlerDelegate.Result.Stop
        }

        return if (BackspaceHandler.isWhitespaceBeforeCaret(editor)) {
            EnterHandlerDelegate.Result.DefaultSkipIndent
        } else EnterHandlerDelegate.Result.Continue
    }

    private fun hasDocEnd(startOffset: Int, file: PsiFile): Boolean {
        for (offset in startOffset..file.endOffset) {
            if (file.findElementAt(offset)?.elementType == DOC_END) {
                return true
            }
        }
        return false
    }

    override fun postProcessEnter(
        file: PsiFile,
        editor: Editor,
        dataContext: DataContext
    ): EnterHandlerDelegate.Result {
        val currentOffset = editor.logicalPositionToOffset(editor.caretModel.logicalPosition)
        insertTabs(file, editor, currentOffset)

        return super.postProcessEnter(file, editor, dataContext)
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
                    val startBrace = when (val parent = brace.parent) {
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

        private fun insertTabs(file: PsiFile, editor: Editor, offset: Int) {
            val document = editor.document
            var element = file.findElementAt(offset - LENGTH_DOC_NEWLINE - 1)
            if (element.elementType == DOC_PARAGRAPH_SEP) {
                return
            }
            while (element != null && element.elementType != DOC_TABS && element.elementType != DOC_NEWLINE && element.elementType != DOC_LINEBREAK) {
                element = element.prevElement
            }

            val nextElement = element?.nextElement ?: return
            val nextElementType = nextElement.elementType
            if (!CONTEXT_ELEMENTS.contains(nextElementType)) {
                return
            }
            val insertedString = if (element.elementType == DOC_TABS) {
                val restSpaces = (element.text.length + AREND_DOC_COMMENT_TABS_SIZE - 1) / AREND_DOC_COMMENT_TABS_SIZE * AREND_DOC_COMMENT_TABS_SIZE - element.text.length
                element.text + " ".repeat(restSpaces)
            } else {
                ""
            } + when (nextElementType) {
                DOC_ORDERED_LIST -> {
                    val number = nextElement.text?.removeSuffix(". ")?.toInt()
                    number?.let { it + 1 }?.toString() + ". "
                }
                else -> nextElement.text
            }
            document.insertString(offset, insertedString)
            editor.caretModel.moveToOffset(offset + insertedString.length)
        }

        private const val LENGTH_DOC_NEWLINE = 4
    }
}
