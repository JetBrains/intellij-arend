package com.jetbrains.arend.ide.actions

import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.jetbrains.arend.ide.ArdIcons

class ArdCreateFileAction : CreateFileFromTemplateAction(CAPTION, "", ArdIcons.AREND_FILE),
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
        builder.setTitle(CAPTION).addKind("Empty File", ArdIcons.AREND_FILE, "Arend File")
    }

    private companion object {
        private val CAPTION = "New Arend File"
    }
}
