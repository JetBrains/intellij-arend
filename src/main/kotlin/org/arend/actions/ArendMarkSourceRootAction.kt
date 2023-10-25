package org.arend.actions

import com.intellij.ide.projectView.actions.MarkRootActionBase
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ui.configuration.ModuleSourceRootEditHandler
import com.intellij.openapi.vfs.VirtualFile
import org.arend.module.config.ArendModuleConfigService
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import java.util.*

class ArendMarkSourceRootAction(type: JpsModuleSourceRootType<*>) : MarkRootActionBase() {
    private val LOG = Logger.getInstance(
        ArendMarkSourceRootAction::class.java
    )
    private var myRootType: JpsModuleSourceRootType<*>

    init {
        myRootType = type
        val presentation = getTemplatePresentation()
        val editHandler = ModuleSourceRootEditHandler.getEditHandler(type)
        LOG.assertTrue(editHandler != null)
        presentation.setIcon(editHandler!!.rootIcon)
        presentation.setText(editHandler.fullRootTypeName)
        presentation.setDescription(
            ProjectBundle.messagePointer(
                "module.toggle.sources.action.description",
                editHandler.fullRootTypeName.lowercase(Locale.getDefault())
            )
        )
    }

    override fun actionPerformed(e: AnActionEvent) {
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (files.isNullOrEmpty()) {
            super.actionPerformed(e)
            return
        }

        val module = e.getData(LangDataKeys.MODULE)
        val arendModuleConfigService = ArendModuleConfigService.getInstance(module)
        val virtualFile = files[0]

        val relativePath = getRelativePath(arendModuleConfigService, virtualFile)

        if (myRootType == JavaSourceRootType.SOURCE) {
            removeOldSourceFolder(module, virtualFile, arendModuleConfigService, myRootType)
            if (relativePath != null) {
                arendModuleConfigService?.updateSourceDirFromIDEA(relativePath)
            }
        } else if (myRootType == JavaSourceRootType.TEST_SOURCE) {
            removeOldSourceFolder(module, virtualFile, arendModuleConfigService, myRootType)
            if (relativePath != null) {
                arendModuleConfigService?.updateTestDirFromIDEA(relativePath)
            }
        }
        super.actionPerformed(e)
    }

    override fun modifyRoots(vFile: VirtualFile, entry: ContentEntry) {
        entry.addSourceFolder(vFile, myRootType)
    }

    override fun isEnabled(selection: RootsSelection, module: Module): Boolean {
        val moduleType = ModuleType.get(module)
        if (!moduleType.isSupportedRootType(myRootType) || ModuleSourceRootEditHandler.getEditHandler(myRootType) == null || selection.myHaveSelectedFilesUnderSourceRoots && !moduleType.isMarkInnerSupportedFor(
                myRootType
            )
        ) {
            return false
        }
        if (selection.mySelectedDirectories.size > 1) {
            return !(myRootType == JavaSourceRootType.SOURCE || myRootType == JavaSourceRootType.TEST_SOURCE)
        } else if (selection.mySelectedDirectories.isNotEmpty()) {
            return true
        }
        for (root in selection.mySelectedRoots) {
            if (myRootType != root.rootType) {
                return true
            }
        }
        return false
    }
}
