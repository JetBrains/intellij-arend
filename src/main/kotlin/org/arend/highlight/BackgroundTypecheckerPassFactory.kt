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
import org.arend.settings.ArendSettings

class BackgroundTypecheckerPassFactory : BasePassFactory<ArendFile>(ArendFile::class.java), TextEditorHighlightingPassFactoryRegistrar {
    private var myPassId = -1

    override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
        myPassId = registrar.registerTextEditorHighlightingPass(this, intArrayOf(project.service<ArendPassFactoryService>().highlightingPassId), null, false, -1)
        project.service<ArendPassFactoryService>().backgroundTypecheckerPassId = myPassId
    }

    override fun createPass(file: ArendFile, editor: Editor, textRange: TextRange): BackgroundTypecheckerPass {
        val isSmart = service<ArendSettings>().typecheckingMode == ArendSettings.TypecheckingMode.SMART
        return BackgroundTypecheckerPass(file, if (isSmart) file else file, editor, if (isSmart) TextRange(0, editor.document.textLength) else textRange, DefaultHighlightInfoProcessor())
    }

    override fun createHighlightingPass(file: PsiFile, editor: Editor) =
        if (file is ArendFile && service<ArendSettings>().typecheckingMode != ArendSettings.TypecheckingMode.OFF) {
            val modCount = file.project.service<ArendPsiChangeService>().definitionModificationTracker.modificationCount
            if (file.lastDefinitionModification < modCount) {
                val pass = super.createHighlightingPass(file, editor)
                if (pass is BackgroundTypecheckerPass) {
                    pass.lastModification = modCount
                }
                pass
            } else null
        } else null

    override fun getPassId() = myPassId
}