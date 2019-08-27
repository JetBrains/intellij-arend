package org.arend.injection

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFileFactory
import org.arend.InjectionTextLanguage
import javax.swing.JComponent

class InjectedArendEditor(text: CharSequence, textRanges: List<List<TextRange>>, project: Project) {
    private val editor: Editor?

    init {
        val psi = PsiFileFactory.getInstance(project).createFileFromText("Dummy.ard", InjectionTextLanguage.INSTANCE, text)
        (psi as? PsiInjectionTextFile)?.injectionRanges = textRanges
        val virtualFile = psi.virtualFile
        editor = if (virtualFile != null) {
            virtualFile.isWritable = false
            PsiDocumentManager.getInstance(project).getDocument(psi)?.let {
                EditorFactory.getInstance().createEditor(it, project, virtualFile, false)
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