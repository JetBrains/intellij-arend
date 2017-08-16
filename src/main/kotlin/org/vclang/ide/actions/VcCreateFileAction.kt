package org.vclang.ide.actions

import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import org.vclang.ide.icons.VcIcons

class VcCreateFileAction : CreateFileFromTemplateAction(CAPTION, "", VcIcons.VCLANG_FILE),
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
        builder.setTitle(CAPTION).addKind("Empty File", VcIcons.VCLANG_FILE, "Vclang File")
    }

    private companion object {
        private val CAPTION = "New Vclang File"
    }
}
