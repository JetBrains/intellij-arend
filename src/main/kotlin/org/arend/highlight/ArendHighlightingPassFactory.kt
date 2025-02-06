package org.arend.highlight

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactoryRegistrar
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.arend.IArendFile
import org.arend.psi.ArendFile
import org.arend.server.ArendServerService

class ArendHighlightingPassFactory : BasePassFactory<IArendFile>(IArendFile::class.java), TextEditorHighlightingPassFactoryRegistrar {
    private var myPassId = -1

    override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
        myPassId = registrar.registerTextEditorHighlightingPass(this, null, null, false, -1)
        project.service<ArendPassFactoryService>().highlightingPassId = myPassId
    }

    override fun createPass(file: IArendFile, editor: Editor, textRange: TextRange): TextEditorHighlightingPass {
        val project = file.project
        val module = (file as? ArendFile)?.moduleLocation
        val data = if (module != null) project.service<ArendServerService>().server.getGroupData(module) else null
        return if (data == null || data.timestamp != file.modificationStamp || !data.isResolved)
            ArendHighlightingPass(file, editor, textRange)
        else
            EmptyHighlightingPass(project, editor.document)
    }

    override fun getPassId() = myPassId
}