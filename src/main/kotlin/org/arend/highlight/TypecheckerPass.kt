package org.arend.highlight

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import org.arend.actions.selectErrorFromEditor
import org.arend.psi.ArendFile
import org.arend.server.ArendServerService

class TypecheckerPass(override val file: ArendFile, editor: Editor)
    : BasePass(file, editor, "Arend typechecker annotator", TextRange(0, editor.document.textLength)) {

    override fun collectInformationWithProgress(progress: ProgressIndicator) {
        val module = file.moduleLocation ?: return
        reportAll(myProject.service<ArendServerService>().server.getTypecheckingErrors(module))
    }

    override fun applyInformationLater() {
        super.applyInformationLater()
        selectErrorFromEditor(file.project, editor, file, always = false, activate = false)
    }
}