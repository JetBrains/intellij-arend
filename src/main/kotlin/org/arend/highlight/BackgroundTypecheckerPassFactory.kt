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
import org.arend.psi.ext.impl.ArendGroup
import org.arend.settings.ArendSettings

class BackgroundTypecheckerPassFactory : BasePassFactory(), TextEditorHighlightingPassFactoryRegistrar {
    private var myPassId = -1

    override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
        myPassId = registrar.registerTextEditorHighlightingPass(this, intArrayOf(project.service<ArendPassFactoryService>().highlightingPassId), null, false, -1)
        project.service<ArendPassFactoryService>().backgroundTypecheckerPassId = myPassId
    }

    override fun createPass(file: ArendFile, group: ArendGroup, editor: Editor, textRange: TextRange): BackgroundTypecheckerPass {
        val isSmart = service<ArendSettings>().typecheckingMode == ArendSettings.TypecheckingMode.SMART
        return BackgroundTypecheckerPass(file, if (isSmart) file else group, editor, if (isSmart) TextRange(0, editor.document.textLength) else textRange, DefaultHighlightInfoProcessor())
    }

    override fun createHighlightingPass(file: PsiFile, editor: Editor) =
        if (service<ArendSettings>().typecheckingMode != ArendSettings.TypecheckingMode.OFF) super.createHighlightingPass(file, editor) else null

    override fun getPassId() = myPassId
}