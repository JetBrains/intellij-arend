package org.arend.highlight

import com.intellij.codeHighlighting.TextEditorHighlightingPassFactoryRegistrar
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.codeInsight.daemon.impl.DefaultHighlightInfoProcessor
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.arend.psi.ArendFile
import org.arend.psi.listener.ArendPsiChangeService

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
            val modCount = file.project.service<ArendPsiChangeService>().modificationTracker.modificationCount
            if (file.lastModification < modCount) {
                val pass = super.createHighlightingPass(file, editor)
                if (pass is ArendHighlightingPass) {
                    pass.lastModification = modCount
                } else {
                    synchronized(ArendHighlightingPass::class.java) {
                        if (file.lastModification < modCount) {
                            file.lastModification = modCount
                        }
                    }
                }
                pass
            } else null
        } else null

    override fun getPassId() = myPassId
}