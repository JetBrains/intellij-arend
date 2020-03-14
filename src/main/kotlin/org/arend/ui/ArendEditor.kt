package org.arend.ui

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import org.arend.ArendFileType

class ArendEditor(
        text: String,
        project: Project? = null,
        readOnly: Boolean = true
) {
    val document: Document
    val editor: Editor

    init {
        val factory = EditorFactory.getInstance()
        document = factory.createDocument(text)
        editor = factory.createEditor(document, project, ArendFileType, readOnly)
    }

    val component get() = editor.component
}
