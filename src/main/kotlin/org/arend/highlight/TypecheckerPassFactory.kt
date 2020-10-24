package org.arend.highlight

import com.intellij.codeHighlighting.TextEditorHighlightingPassFactoryRegistrar
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.codeInsight.daemon.impl.DefaultHighlightInfoProcessor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.arend.psi.ArendFile
import org.arend.psi.listener.ArendPsiChangeService

class TypecheckerPassFactory : BasePassFactory<ArendFile>(ArendFile::class.java), TextEditorHighlightingPassFactoryRegistrar {
    private var myPassId = -1

    override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
        val service = project.service<ArendPassFactoryService>()
        myPassId = registrar.registerTextEditorHighlightingPass(this, intArrayOf(service.highlightingPassId, service.backgroundTypecheckerPassId), null, false, -1)
    }

    override fun allowWhiteSpaces() = true

    override fun createPass(file: ArendFile, editor: Editor, textRange: TextRange) =
        if (file.lastDefinitionModification >= file.project.service<ArendPsiChangeService>().definitionModificationTracker.modificationCount || ApplicationManager.getApplication().isUnitTestMode) {
            TypecheckerPass(file, editor, DefaultHighlightInfoProcessor())
        } else {
            TypecheckerPass.updateErrors(file)
            null
        }

    override fun getPassId() = myPassId
}