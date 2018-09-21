package org.arend.actions

import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import org.arend.ArendIcons

class ArendCreateFileAction : CreateFileFromTemplateAction(CAPTION, "", ArendIcons.AREND_FILE),
                           DumbAware {

    override fun getActionName(
            directory: PsiDirectory?,
            newName: String?,
            templateName: String?
    ): String = CAPTION

    override fun buildDialog(
            project: Project?,
            directory: PsiDirectory?,
            builder: CreateFileFromTemplateDialog.Builder
    ) {
        builder.setTitle(CAPTION).addKind("Empty File", ArendIcons.AREND_FILE, "Arend File")
    }

    private companion object {
        private const val CAPTION = "New Arend File"
    }
}
