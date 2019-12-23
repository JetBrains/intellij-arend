package org.arend.module.config

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import org.arend.library.LibraryDependency
import org.arend.util.FileUtils
import org.jetbrains.yaml.psi.YAMLFile


class ArendModuleConfigurationUpdater(private val isNewModule: Boolean) : ModuleBuilder.ModuleConfigurationUpdater(), ArendModuleConfiguration {
    override var librariesRoot = ""
    override var sourcesDir = ""
    override var withBinaries = false
    override var binariesDirectory = ""
    override var withExtensions = false
    override var extensionsDirectory = ""
    override var extensionMainClassData = ""
    override var dependencies: List<LibraryDependency> = emptyList()
    override var langVersionString = ""

    override fun update(module: Module, rootModel: ModifiableRootModel) {
        val contentEntry = rootModel.contentEntries.firstOrNull() ?: return
        val moduleRoot = contentEntry.file ?: return
        val configService = ArendModuleConfigService.getInstance(module) ?: return

        if (isNewModule) {
            VfsUtil.saveText(moduleRoot.findOrCreateChildData(moduleRoot, FileUtils.LIBRARY_CONFIG_FILE),
                (if (langVersionString.isNotEmpty()) "$LANG_VERSION: $langVersionString\n" else "") +
                "$SOURCES: $sourcesDir" +
                (if (withBinaries) "\n$BINARIES: $binariesDirectory" else "") +
                (if (withExtensions) "\n$EXTENSIONS: $extensionsDirectory" else "") +
                (if (withExtensions) "\n$EXTENSION_MAIN: $extensionMainClassData" else "") +
                (if (dependencies.isNotEmpty()) "\n$DEPENDENCIES: ${yamlSeqFromList(dependencies.map { it.name })}" else ""))
            configService.copyFrom(this)
        } else {
            val yaml = moduleRoot.findChild(FileUtils.LIBRARY_CONFIG_FILE)?.let {
                PsiManager.getInstance(rootModel.project).findFile(it) as? YAMLFile
            } ?: return

            configService.copyFromYAML(yaml, false)
            copyFrom(configService)
        }

        val rootPath = FileUtil.toSystemDependentName(moduleRoot.path)
        val srcDir = toAbsolute(rootPath, sourcesDir)
        val binDir = toAbsolute(rootPath, binariesDirectory)
        val extDir = toAbsolute(rootPath, extensionsDirectory)

        VfsUtil.createDirectories(srcDir)
        contentEntry.addSourceFolder(VfsUtil.pathToUrl(srcDir), false)
        contentEntry.addExcludeFolder(VfsUtil.pathToUrl(binDir))
        contentEntry.addExcludeFolder(VfsUtil.pathToUrl(extDir))
    }
}
