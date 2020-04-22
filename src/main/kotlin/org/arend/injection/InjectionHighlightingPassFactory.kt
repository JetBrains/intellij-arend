package org.arend.injection

import com.intellij.codeHighlighting.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.arend.highlight.BasePassFactory

class InjectionHighlightingPassFactory : BasePassFactory<PsiInjectionTextFile>(PsiInjectionTextFile::class.java), TextEditorHighlightingPassFactoryRegistrar {
    private var myPassId = -1

    override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
        myPassId = registrar.registerTextEditorHighlightingPass(this, null, null, false, -1)
    }

    override fun getPassId() = myPassId

    override fun createPass(file: PsiInjectionTextFile, editor: Editor, textRange: TextRange) =
        if (file.hasInjection) InjectionHighlightingPass(file, editor) else null
}