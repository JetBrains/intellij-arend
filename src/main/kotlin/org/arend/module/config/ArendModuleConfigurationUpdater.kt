package org.arend.module.config

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import org.arend.library.LibraryDependency
import org.arend.util.FileUtils


class ArendModuleConfigurationUpdater(private val isNewModule: Boolean) : ModuleBuilder.ModuleConfigurationUpdater(), ArendModuleConfiguration {
    override var librariesRoot = ""
    override var sourcesDir = ""
    override var withBinaries = false
    override var binariesDirectory = ""
    override var dependencies: List<LibraryDependency> = emptyList()

    override fun update(module: Module, rootModel: ModifiableRootModel) {
        if (!isNewModule) {
            copyFrom(ArendModuleConfigService.getInstance(module) ?: return)
        }

        val contentEntry = rootModel.contentEntries.firstOrNull() ?: return
        val moduleRoot = contentEntry.file ?: return
        val rootPath = FileUtil.toSystemDependentName(moduleRoot.path)
        val srcDir = toAbsolute(rootPath, sourcesDir)
        val binDir = toAbsolute(rootPath, binariesDirectory)

        VfsUtil.createDirectories(srcDir)
        contentEntry.addSourceFolder(VfsUtil.pathToUrl(srcDir), false)
        contentEntry.addExcludeFolder(VfsUtil.pathToUrl(binDir))

        if (isNewModule) {
            VfsUtil.saveText(moduleRoot.findOrCreateChildData(moduleRoot, FileUtils.LIBRARY_CONFIG_FILE),
                "$SOURCES: $sourcesDir" +
                (if (withBinaries) "\n$BINARIES: $binariesDirectory" else "") +
                (if (dependencies.isNotEmpty()) "\n$DEPENDENCIES: ${yamlSeqFromList(dependencies.map { it.name })}" else ""))
            ArendModuleConfigService.getInstance(module)?.copyFrom(this)
        }
    }
}
