package org.arend.actions.mark

import com.intellij.ide.projectView.actions.UnmarkRootAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import org.arend.module.config.ArendModuleConfigService
import org.arend.util.ArendBundle
import org.arend.util.allModules

open class ArendUnmarkRootAction : UnmarkRootAction() {

    override fun doUpdate(e: AnActionEvent, module: Module?, selection: RootsSelection) {
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: emptyArray()
        val arendModuleConfigService = ArendModuleConfigService.getInstance(module)
        if (files.size == 1 && files[0].name == arendModuleConfigService?.binariesDirectory) {
            e.presentation.isEnabledAndVisible = true
            e.presentation.text = ArendBundle.message("arend.icon.modules.binRoot.unmark")
            return
        } else if (files.map { it.name }.contains(arendModuleConfigService?.binariesDirectory)) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        super.doUpdate(e, module, selection)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (files.isNullOrEmpty()) {
            super.actionPerformed(e)
            return
        }
        val file = files[0]

        val module = e.getData(LangDataKeys.MODULE) ?:
            if (e.presentation.text == ArendBundle.message("arend.icon.modules.binRoot.unmark")) {
                e.project?.allModules?.find {
                    ArendModuleConfigService.getInstance(it)?.binariesDirFile == file
                }
            } else {
                null
            }
        val arendModuleConfigService = ArendModuleConfigService.getInstance(module)
        if (e.presentation.text == ArendBundle.message("arend.icon.modules.binRoot.unmark")) {
            arendModuleConfigService?.updateBinDirFromIDEA("")
        } else {
            val model = module?.let { ModuleRootManager.getInstance(it).modifiableModel } ?: run {
                super.actionPerformed(e)
                return
            }
            val sourceFolders = findContentEntry(model, file)?.sourceFolders
            if (!sourceFolders.isNullOrEmpty()) {
                sourceFolders.find { it.file == file }?.let {
                    if (it.isTestSource) {
                        arendModuleConfigService?.updateTestDirFromIDEA("")
                    } else {
                        arendModuleConfigService?.updateSourceDirFromIDEA("")
                    }
                }
            }
        }
        super.actionPerformed(e)
    }
}
