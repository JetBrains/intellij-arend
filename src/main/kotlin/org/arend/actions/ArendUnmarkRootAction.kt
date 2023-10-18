package org.arend.actions

import com.intellij.ide.projectView.actions.MarkRootActionBase
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ui.configuration.ModuleSourceRootEditHandler
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import org.arend.module.config.ArendModuleConfigService

open class ArendUnmarkRootAction : MarkRootActionBase() {
    override fun doUpdate(e: AnActionEvent, module: Module?, selection: RootsSelection) {
        if (!Registry.`is`("ide.hide.excluded.files") && !selection.mySelectedExcludeRoots.isEmpty()
            && selection.mySelectedDirectories.isEmpty() && selection.mySelectedRoots.isEmpty()
        ) {
            e.presentation.setEnabledAndVisible(true)
            e.presentation.setText(LangBundle.messagePointer("mark.as.unmark.excluded"))
            return
        }
        super.doUpdate(e, module, selection)
        val text = getActionText(e, module, selection)
        if (text != null) e.presentation.text = text
    }

    override fun actionPerformed(e: AnActionEvent) {
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (files.isNullOrEmpty()) {
            super.actionPerformed(e)
            return
        }

        val module = e.getData(LangDataKeys.MODULE)
        val arendModuleConfigService = ArendModuleConfigService.getInstance(module)
        val model = module?.let { ModuleRootManager.getInstance(it).modifiableModel } ?: run {
            super.actionPerformed(e)
            return
        }
        setEmptySourceDirectories(model, arendModuleConfigService, files)

        super.actionPerformed(e)
    }

    private fun setEmptySourceDirectories(model: ModifiableRootModel, arendModuleConfigService: ArendModuleConfigService?, files: Array<VirtualFile>) {
        for (file in files) {
            findContentEntry(model, file)?.sourceFolders?.let { sourceFolders ->
                for (sourceFolder in sourceFolders) {
                    if (file == sourceFolder.file) {
                        if (sourceFolder.isTestSource) {
                            arendModuleConfigService?.updateTestDirFromIDEA("")
                        } else {
                            arendModuleConfigService?.updateSourceDirFromIDEA("")
                        }
                    }
                }
            }
        }
    }

    protected fun getActionText(
        e: AnActionEvent,
        module: Module?,
        selection: RootsSelection
    ): @NlsActions.ActionText String? {
        val selectedRootHandlers = getHandlersForSelectedRoots(selection)
        return if (selectedRootHandlers.isNotEmpty()) {
            if (selectedRootHandlers.size == 1) {
                val handler = selectedRootHandlers.iterator().next()
                LangBundle.message(
                    "mark.as.unmark",
                    StringUtil.pluralize(handler.fullRootTypeName, selection.mySelectedRoots.size)
                )
            } else {
                LangBundle.message("mark.as.unmark.several")
            }
        } else null
    }

    private fun getHandlersForSelectedRoots(selection: RootsSelection): Set<ModuleSourceRootEditHandler<*>> {
        val selectedRootHandlers = HashSet<ModuleSourceRootEditHandler<*>>()
        for (root in selection.mySelectedRoots) {
            ContainerUtil.addIfNotNull(selectedRootHandlers, ModuleSourceRootEditHandler.getEditHandler(root.rootType))
        }
        return selectedRootHandlers
    }

    override fun isEnabled(selection: RootsSelection, module: Module): Boolean {
        return selection.mySelectedDirectories.isEmpty() && getHandlersForSelectedRoots(selection).isNotEmpty()
    }

    override fun modifyRoots(file: VirtualFile, entry: ContentEntry) {
        for (excludeFolder in entry.excludeFolders) {
            if (file == excludeFolder.file) {
                entry.removeExcludeFolder(excludeFolder)
                break
            }
        }
    }
}
