package org.arend.highlight

import com.intellij.codeHighlighting.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.arend.psi.ArendFile

class ArendUnusedImportHighlightingPassFactory : TextEditorHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar {
    override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
        registrar.registerTextEditorHighlightingPass(this, intArrayOf(Pass.UPDATE_ALL), null, true, -1)
    }

    override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass {
        if (file !is ArendFile) return EmptyHighlightingPass(file.project, editor.document)
        return ArendUnusedImportHighlightingPass(file, editor)
    }
}