package org.arend.actions

import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.projectView.actions.MarkRootActionBase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.arend.module.config.ArendModuleConfigService
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import java.io.File

internal fun getRelativePath(arendModuleConfigService: ArendModuleConfigService?, virtualFile: VirtualFile?): String? {
    return arendModuleConfigService?.root?.path?.let { File(it) }
        ?.let { virtualFile?.path?.let { path -> File(path).relativeTo(it).path } }
}

internal fun removeOldSourceFolder(module: Module?, virtualFile: VirtualFile?, arendModuleConfigService: ArendModuleConfigService?, rootType: JpsModuleSourceRootType<*>) {
    val model = module?.let { ModuleRootManager.getInstance(it).modifiableModel }
    val entry = model?.let { rootModel -> virtualFile?.let { file -> MarkRootActionBase.findContentEntry(rootModel, file) } }
    val oldDir = if (rootType == JavaSourceRootType.SOURCE) {
        arendModuleConfigService?.sourcesDir
    } else {
        arendModuleConfigService?.testsDir
    }
    entry?.sourceFolders?.find {
        getRelativePath(arendModuleConfigService, it.file) == oldDir
    }?.let {
        entry.removeSourceFolder(it)
    }
    commitModel(module, model)
}

internal fun addNewSourceFolder(module: Module?, virtualFile: VirtualFile, rootType: JpsModuleSourceRootType<*>) {
    val model = module?.let { ModuleRootManager.getInstance(it).modifiableModel }
    val entry = model?.let { MarkRootActionBase.findContentEntry(it, virtualFile) }
    entry?.addSourceFolder(virtualFile, rootType)
    commitModel(module, model)
}

internal fun commitModel(module: Module?, model: ModifiableRootModel?) {
    ApplicationManager.getApplication().runWriteAction { model?.commit() }
    module?.project?.let { SaveAndSyncHandler.getInstance().scheduleProjectSave(it) }
}

internal fun removeOldDirectory(module: Module?, file: VirtualFile?, arendModuleConfigService: ArendModuleConfigService?, rootType: JpsModuleSourceRootType<*>) {
    val dir = if (rootType == JavaSourceRootType.SOURCE) {
        arendModuleConfigService?.sourcesDir
    } else {
        arendModuleConfigService?.testsDir
    }
    if (!dir.isNullOrEmpty()) {
        removeOldSourceFolder(module, file, arendModuleConfigService, rootType)
    }
}

internal fun addNewDirectory(dir: String?, arendModuleConfigService: ArendModuleConfigService?, rootType: JpsModuleSourceRootType<*>) {
    if (!dir.isNullOrEmpty()) {
        val dirFile = File(arendModuleConfigService?.root?.path + File.separator + dir)
        LocalFileSystem.getInstance().findFileByIoFile(dirFile)?.let {
            addNewSourceFolder(arendModuleConfigService?.module, it, rootType)
        }
    }
}
