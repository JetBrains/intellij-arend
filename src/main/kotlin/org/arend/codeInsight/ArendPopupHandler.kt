package org.arend.codeInsight

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import org.arend.ui.ArendEditor


abstract class ArendPopupHandler(private val requestFocus: Boolean) : CodeInsightActionHandler {
    private companion object {
        private var popup: JBPopup? = null
    }

    override fun startInWriteAction() = false

    fun displayErrorHint(editor: Editor, text: String) = ApplicationManager.getApplication().invokeLater {
        HintManager.getInstance().apply {
            setRequestFocusForNextHint(requestFocus)
            showErrorHint(editor, text)
        }
    }

    fun displayEditorHint(
            text: String,
            project: Project,
            editor: Editor,
            adText: String
    ) = ApplicationManager.getApplication().invokeLater {
        popup?.cancel()
        val arendEditor = ArendEditor(text, project, readOnly = true)
        arendEditor.editor.settings.setGutterIconsShown(false)
        arendEditor.editor.settings.isLineNumbersShown = false
        arendEditor.editor.settings.isLineMarkerAreaShown = false
        arendEditor.editor.settings.isCaretRowShown = false
        arendEditor.editor.isRendererMode = true
        arendEditor.editor.settings.additionalColumnsCount = 1
        arendEditor.editor.settings.isFoldingOutlineShown = false
        arendEditor.editor.settings.additionalLinesCount = 1
        arendEditor.editor.setFontSize(EditorColorsManager.getInstance().globalScheme.editorFontSize)
        val factory = JBPopupFactory.getInstance()
        val listener = object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) = arendEditor.close()
        }
        factory.createComponentPopupBuilder(arendEditor.component, null)
                .setFocusable(true)
                .setProject(project)
                .setAdText(adText)
                .setMovable(true)
                .setRequestFocus(requestFocus)
                .createPopup()
                .also { popup = it }
                .apply { addListener(listener) }
                .show(factory.guessBestPopupLocation(editor))
    }
}
