package org.arend.injection

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.impl.EditorHyperlinkSupport
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFileFactory
import org.arend.InjectionTextLanguage
import org.arend.error.GeneralError
import javax.swing.JComponent

class InjectedArendEditor(text: CharSequence, injectedTextRanges: List<List<TextRange>>, hyperlinks: List<Pair<TextRange, HyperlinkInfo>>, project: Project, val error: GeneralError) {
    private val editor: Editor?

    init {
        val psi = PsiFileFactory.getInstance(project).createFileFromText("Error Message", InjectionTextLanguage.INSTANCE, text)
        (psi as? PsiInjectionTextFile)?.injectionRanges = injectedTextRanges
        val virtualFile = psi.virtualFile
        editor = if (virtualFile != null) {
            virtualFile.isWritable = false
            PsiDocumentManager.getInstance(project).getDocument(psi)?.let { document ->
                EditorFactory.getInstance().createEditor(document, project, virtualFile, false)?.also {
                    val support = EditorHyperlinkSupport.get(it)
                    if (hyperlinks.isNotEmpty()) {
                        for (hyperlink in hyperlinks) {
                            support.createHyperlink(hyperlink.first.startOffset, hyperlink.first.endOffset, null, hyperlink.second)
                        }
                    }
                }
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