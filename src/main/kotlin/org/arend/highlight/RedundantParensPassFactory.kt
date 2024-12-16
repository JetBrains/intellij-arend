package org.arend.highlight

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactoryRegistrar
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.arend.psi.ArendFile
import org.arend.util.checkArcFile

class RedundantParensPassFactory: BasePassFactory<ArendFile>(ArendFile::class.java), TextEditorHighlightingPassFactoryRegistrar {
    private var myPassId = -1
    override fun createPass(file: ArendFile, editor: Editor, textRange: TextRange): TextEditorHighlightingPass? {
        if (checkArcFile(file)) {
            return null
        }
        return RedundantParensPass(file, editor)
    }

    override fun getPassId(): Int = myPassId

    override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
        val service = project.service<ArendPassFactoryService>()
        myPassId = registrar.registerTextEditorHighlightingPass(this, intArrayOf(service.highlightingPassId), null, false, -1)
        service.typecheckerPassId = myPassId
    }
}