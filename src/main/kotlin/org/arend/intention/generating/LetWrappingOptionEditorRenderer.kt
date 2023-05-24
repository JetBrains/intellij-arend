@file:Suppress("UnstableApiUsage")

package org.arend.intention.generating

import com.intellij.codeInsight.unwrap.ScopeHighlighter
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import java.util.concurrent.atomic.AtomicReference

internal class LetWrappingOptionEditorRenderer(
        private val editor: Editor,
        private val project: Project,
        private val commandGroupId: String?
) : Disposable {
    companion object {
        @NlsSafe private const val TEXT = "\\let … \\in "
    }

    private val insertedRangeReference: AtomicReference<TextRange?> = AtomicReference(null)
    private val highlighterReference: AtomicReference<ScopeHighlighter?> = AtomicReference(ScopeHighlighter(editor))

    private fun executeWriteCommand(action: () -> Unit) {
        executeCommand(project, null, commandGroupId) { runWriteAction(action) }
    }

    fun cleanup() {
        val range = insertedRangeReference.getAndSet(null)
        if (range != null) {
            executeWriteCommand {
                editor.document.deleteString(range.startOffset, range.endOffset)
                PsiDocumentManager.getInstance(project).commitDocument(editor.document)
            }
        }
        highlighterReference.getAndSet(null)?.dropHighlight()
    }

    /**
     * @param offset Global text offset of an expression that should be wrapped
     */
    fun renderOption(offset: Int, parentLet: TextRange?) {
        cleanup()
        val document = editor.document
        if (parentLet == null) {
            insertDummyLet(document, offset)
        } else {
            highlightExistingLet(parentLet)
        }

    }

    private fun highlightExistingLet(parentLet: TextRange?) {
        runReadAction {
            val newHighlighter = ScopeHighlighter(editor)
            highlighterReference.set(newHighlighter)
            newHighlighter.highlight(Pair.create(parentLet, listOf(parentLet)))
        }
    }

    private fun insertDummyLet(document: Document, offset: Int) {
        executeWriteCommand {
            document.insertString(offset, TEXT)
            PsiDocumentManager.getInstance(project).commitDocument(document)
            val range = TextRange(offset, offset + TEXT.length)
            insertedRangeReference.set(range)
            val newHighlighter = ScopeHighlighter(editor)
            highlighterReference.set(newHighlighter)
            val rangeToHighlight = range.grown(-1)
            newHighlighter.highlight(Pair.create(rangeToHighlight, listOf(rangeToHighlight)))
        }
    }

    override fun dispose() = cleanup()
}