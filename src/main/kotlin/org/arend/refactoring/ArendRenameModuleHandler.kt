package org.arend.refactoring

import com.intellij.ide.IdeBundle
import com.intellij.ide.projectView.impl.RenameModuleHandler
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleWithNameAlreadyExists
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import org.arend.util.FileUtils

class ArendRenameModuleHandler: RenameModuleHandler() {
    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext) {
        val module = LangDataKeys.MODULE_CONTEXT.getData(dataContext) ?: return
        Messages.showInputDialog(project,
                IdeBundle.message("prompt.enter.new.module.name"),
                IdeBundle.message("title.rename.module"),
                Messages.getQuestionIcon(),
                module.name,
                object: InputValidator {
                    override fun checkInput(inputString: String): Boolean = true

                    override fun canClose(inputString: String): Boolean {
                        if (!FileUtils.isLibraryName(inputString)) {
                            Messages.showErrorDialog(project, LangBundle.message("incorrect.name"),
                                    IdeBundle.message("title.rename.module"))
                            return false
                        }
                        val oldName = module.name
                        val modifiableModel = renameModule(inputString) ?: return false
                        CommandProcessor.getInstance().executeCommand(project, { ApplicationManager.getApplication().runWriteAction { modifiableModel.commit() } }, IdeBundle.message("command.renaming.module", oldName), null)
                        return true
                    }

                    private fun renameModule(inputString: String): ModifiableModuleModel? {
                        val modifiableModel = ModuleManager.getInstance(project).getModifiableModel()
                        try {
                            modifiableModel.renameModule(module, inputString)
                        } catch (moduleWithNameAlreadyExists: ModuleWithNameAlreadyExists) {
                            Messages.showErrorDialog(project, IdeBundle.message("error.module.already.exists", inputString),
                                    IdeBundle.message("title.rename.module"))
                            return null
                        }

                        return modifiableModel
                    }

                })
    }

}