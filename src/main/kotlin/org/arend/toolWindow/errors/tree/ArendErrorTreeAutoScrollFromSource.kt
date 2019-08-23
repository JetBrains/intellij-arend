package org.arend.toolWindow.errors.tree

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.ui.AutoScrollFromSourceHandler
import org.arend.editor.ArendOptions
import org.arend.error.GeneralError
import org.arend.typechecking.error.ErrorService


class ArendErrorTreeAutoScrollFromSource(private val project: Project, private val tree: ArendErrorTree) : AutoScrollFromSourceHandler(project, tree, project) {
    init {
        install()
    }

    override fun install() {
        EditorFactory.getInstance().eventMulticaster.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                selectInAlarm(event.editor)
            }
        }, project)
    }

    private fun selectInAlarm(editor: Editor?) {
        if (editor != null && tree.isShowing && isAutoScrollEnabled) {
            myAlarm.cancelAllRequests()
            myAlarm.addRequest({ selectElementFromEditor(editor) }, alarmDelay, modalityState)
        }
    }

    private fun selectElementFromEditor(editor: Editor) {
        if (editor.project != project) {
            return
        }

        val document = editor.document
        val ranges = ErrorService.getInstance(project).getRanges(document) ?: return
        val offset = editor.caretModel.offset
        var currentError: GeneralError? = null
        (DocumentMarkupModel.forDocument(document, project, true) as? MarkupModelEx)?.processRangeHighlightersOverlappingWith(offset, offset) {
            val errors = ranges[it] ?: return@processRangeHighlightersOverlappingWith true
            for (error in errors) {
                if (currentError == null || error.level > currentError!!.level) {
                    currentError = error
                }
                if (currentError?.level == GeneralError.Level.ERROR) {
                    return@processRangeHighlightersOverlappingWith false
                }
            }
            true
        }

        currentError?.let { tree.select(it) }
    }

    override fun selectElementFromEditor(editor: FileEditor) {
        (editor as? TextEditor)?.editor?.let { selectElementFromEditor(it) }
    }

    override fun setAutoScrollEnabled(enabled: Boolean) {
        ArendOptions.instance.autoScrollFromSource = enabled
    }

    override fun isAutoScrollEnabled() = ArendOptions.instance.autoScrollFromSource
}