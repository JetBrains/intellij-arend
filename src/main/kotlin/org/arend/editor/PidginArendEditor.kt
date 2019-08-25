package org.arend.editor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFileFactory
import org.arend.ArendLanguage
import javax.swing.JComponent

class PidginArendEditor(text: CharSequence, project: Project) {
    private val editor: Editor?

    init {
        val psi = PsiFileFactory.getInstance(project).createFileFromText("Dummy.ard", ArendLanguage.INSTANCE, text)
        val virtualFile = psi.virtualFile
        editor = if (virtualFile != null) {
            PsiDocumentManager.getInstance(project).getDocument(psi)?.let {
                EditorFactory.getInstance().createEditor(it, project, virtualFile, true)
            }
        } else null
    }

    fun release() {
        if (editor != null) {
            EditorFactory.getInstance().releaseEditor(editor)
        }
    }

    val component: JComponent?
        get() = editor?.component
}