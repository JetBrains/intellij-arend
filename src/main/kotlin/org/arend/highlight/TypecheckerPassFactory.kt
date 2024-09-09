package org.arend.highlight

import com.intellij.codeHighlighting.TextEditorHighlightingPassFactoryRegistrar
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.arend.psi.ArendFile

class TypecheckerPassFactory : BasePassFactory<ArendFile>(ArendFile::class.java), TextEditorHighlightingPassFactoryRegistrar {
    private var myPassId = -1

    override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
        val service = project.service<ArendPassFactoryService>()
        myPassId = registrar.registerTextEditorHighlightingPass(this, intArrayOf(service.highlightingPassId), null, false, -1)
        service.typecheckerPassId = myPassId
    }

    override fun allowWhiteSpaces() = true

    override fun createPass(file: ArendFile, editor: Editor, textRange: TextRange) =
        if (file.isBackgroundTypecheckingFinished || ApplicationManager.getApplication().isUnitTestMode) {
            TypecheckerPass(file, editor)
        } else {
            TypecheckerPass.updateErrors(file)
            null
        }

    override fun getPassId() = myPassId
}