package org.arend.actions

import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.psi.PsiDirectory
import org.arend.ArendIcons
import org.arend.util.FileUtils

class ArendCreateFileAction : CreateFileFromTemplateAction(CAPTION, "", ArendIcons.AREND_FILE),
                           DumbAware {

    override fun getActionName(
            directory: PsiDirectory?,
            newName: String,
            templateName: String?
    ): String = CAPTION

    override fun buildDialog(
            project: Project,
            directory: PsiDirectory,
            builder: CreateFileFromTemplateDialog.Builder
    ) {
        builder.setTitle(CAPTION).addKind("Empty File", ArendIcons.AREND_FILE, "Arend File")
        builder.setValidator(object: InputValidator {
            override fun checkInput(inputString: String): Boolean = true
                //FileUtils.isModuleName(inputString)

            override fun canClose(inputString: String?): Boolean =
                    FileUtils.isModuleName(inputString)

        })
    }

    private companion object {
        private const val CAPTION = "Arend File"
    }
}
