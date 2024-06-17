package org.arend.ui

import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import org.arend.ArendFileTypeInstance
import org.arend.settings.ArendProjectSettings

class ArendEditor(
        text: String,
        project: Project? = null,
        readOnly: Boolean = true,
) : AutoCloseable {
    private val factory = EditorFactory.getInstance()
    private val document = factory.createDocument(text)
    val editor = factory.createEditor(document, project, EditorKind.PREVIEW) as EditorEx

    init {
        editor.isViewer = readOnly
        editor.highlighter = EditorHighlighterFactory
                .getInstance()
                .createEditorHighlighter(project, ArendFileTypeInstance)
        project?.serviceIfCreated<ArendProjectSettings>()?.run {
            editor.setFontSize(data.popupFontSize)
        }
    }

    override fun close() {
        val editorImpl = editor as? EditorImpl
        if (editorImpl != null) {
            editorImpl.project?.serviceIfCreated<ArendProjectSettings>()?.run {
                data.popupFontSize = editorImpl.fontSize
            }
        }
        factory.releaseEditor(editor)
    }

    val component get() = editor.component
}
