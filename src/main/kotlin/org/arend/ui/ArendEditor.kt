package org.arend.ui

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.project.Project
import org.arend.ArendFileType

class ArendEditor(
        text: String,
        project: Project? = null,
        readOnly: Boolean = true
) : AutoCloseable {
    private val factory = EditorFactory.getInstance()
    private val document = factory.createDocument(text)
    private val editor = factory.createEditor(document, project, EditorKind.PREVIEW) as EditorEx

    init {
        editor.isViewer = readOnly
        editor.highlighter = EditorHighlighterFactory
                .getInstance()
                .createEditorHighlighter(project, ArendFileType)
    }

    override fun close() = factory.releaseEditor(editor)

    val component get() = editor.component
}
