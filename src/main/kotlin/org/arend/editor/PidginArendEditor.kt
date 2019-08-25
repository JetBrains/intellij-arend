package org.arend.editor

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project

class PidginArendEditor(text: CharSequence, project: Project) {
    val editor = EditorFactory.getInstance().let {
        it.createViewer(it.createDocument(text), project)!!
    }

    fun release() {
        EditorFactory.getInstance().releaseEditor(editor)
    }
}