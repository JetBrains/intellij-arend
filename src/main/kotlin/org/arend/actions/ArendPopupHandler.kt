package org.arend.actions

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import org.arend.ui.ArendEditor


abstract class ArendPopupHandler(private val requestFocus: Boolean) : CodeInsightActionHandler {
    private companion object {
        var popup: JBPopup? = null
    }

    override fun startInWriteAction() = false

    fun displayErrorHint(editor: Editor, text: String) = ApplicationManager.getApplication().invokeLater {
        HintManager.getInstance().apply {
            setRequestFocusForNextHint(requestFocus)
            showErrorHint(editor, text)
        }
    }

    fun displayEditorHint(text: String, project: Project, editor: Editor) = ApplicationManager.getApplication().invokeLater {
        val arendEditor = ArendEditor(text, project, readOnly = true)
        val factory = JBPopupFactory.getInstance()
        popup?.cancel()
        factory.createComponentPopupBuilder(arendEditor.component, arendEditor.component)
                .setFocusable(true)
                .setProject(project)
                .setRequestFocus(requestFocus)
                .createPopup()
                .also { popup = it }
                .show(factory.guessBestPopupLocation(editor))
    }
}
