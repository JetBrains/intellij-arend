package org.arend.highlight

import com.intellij.codeHighlighting.*
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiModificationTracker
import org.arend.psi.ArendFile

class ArendUnusedImportHighlightingPassFactory : TextEditorHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar {
    override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
        val service = project.service<ArendPassFactoryService>()
        registrar.registerTextEditorHighlightingPass(this, intArrayOf(service.typecheckerPassId), null, true, -1)
    }

    override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass? {
        if (file !is ArendFile) return null
        val modCount = PsiModificationTracker.getInstance(file.project).modificationCount
        if (file.lastModificationImportOptimizer.get() >= modCount) return null
        return ArendUnusedImportHighlightingPass(file, editor, modCount)
    }
}