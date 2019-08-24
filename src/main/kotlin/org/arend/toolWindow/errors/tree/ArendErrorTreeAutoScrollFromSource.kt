package org.arend.toolWindow.errors.tree

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.AutoScrollFromSourceHandler
import com.intellij.util.Processor
import org.arend.highlight.BasePass
import org.arend.psi.ArendFile
import org.arend.settings.ArendProjectSettings
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
        val offset = editor.caretModel.offset
        // Check that we are in a problem range
        if ((DocumentMarkupModel.forDocument(document, project, true) as? MarkupModelEx)?.processRangeHighlightersOverlappingWith(offset, offset, Processor.FALSE) == true) {
            return
        }

        val file = PsiDocumentManager.getInstance(project).getPsiFile(document) as? ArendFile ?: return
        val arendErrors = project.service<ErrorService>().getErrors(file)
        if (arendErrors.isEmpty()) {
            return
        }

        for (arendError in arendErrors) {
            if (BasePass.getImprovedTextRange(arendError.error)?.contains(offset) == true) {
                tree.select(arendError.error)
                break
            }
        }
    }

    override fun selectElementFromEditor(editor: FileEditor) {
        (editor as? TextEditor)?.editor?.let { selectElementFromEditor(it) }
    }

    override fun setAutoScrollEnabled(enabled: Boolean) {
        project.service<ArendProjectSettings>().autoScrollFromSource = enabled
    }

    override fun isAutoScrollEnabled() = project.service<ArendProjectSettings>().autoScrollFromSource
}