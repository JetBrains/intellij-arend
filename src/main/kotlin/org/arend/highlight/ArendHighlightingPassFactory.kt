package org.arend.highlight

import com.intellij.codeHighlighting.TextEditorHighlightingPassFactoryRegistrar
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.codeInsight.daemon.impl.DefaultHighlightInfoProcessor
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiModificationTracker
import org.arend.psi.ArendFile

class ArendHighlightingPassFactory : BasePassFactory<ArendFile>(ArendFile::class.java), TextEditorHighlightingPassFactoryRegistrar {
    private var myPassId = -1

    override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
        myPassId = registrar.registerTextEditorHighlightingPass(this, null, null, false, -1)
        project.service<ArendPassFactoryService>().highlightingPassId = myPassId
    }

    override fun createPass(file: ArendFile, editor: Editor, textRange: TextRange) =
        ArendHighlightingPass(file, editor, textRange, DefaultHighlightInfoProcessor())

    override fun createHighlightingPass(file: PsiFile, editor: Editor) =
        if (file is ArendFile) {
            val modCount = PsiModificationTracker.getInstance(file.project).modificationCount
            if (file.lastModification.get() < modCount) {
                val pass = super.createHighlightingPass(file, editor)
                if (pass is ArendHighlightingPass) {
                    pass.lastModification = modCount
                } else {
                    file.lastModification.updateAndGet { maxOf(it, modCount) }
                }
                pass
            } else null
        } else null

    override fun getPassId() = myPassId
}