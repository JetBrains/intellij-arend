package org.arend.module.config

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import org.arend.library.LibraryDependency
import org.arend.module.editor.ArendModuleConfigurationView
import org.arend.util.FileUtils
import org.arend.util.allModules
import org.arend.yaml.*
import org.jetbrains.yaml.psi.YAMLFile


class ArendModuleConfigurationUpdater(private val isNewModule: Boolean) : ModuleBuilder.ModuleConfigurationUpdater(), ArendModuleConfiguration {
    override var librariesRoot = ""
    override var sourcesDir = ""
    override var testsDir = ""
    override var withBinaries = false
    override var binariesDirectory = ""
    override var withExtensions = false
    override var extensionsDirectory = ""
    override var extensionMainClassData = ""
    override var dependencies: List<LibraryDependency> = emptyList()
    override var versionString = ""
    override var langVersionString = ""

    override fun update(module: Module, rootModel: ModifiableRootModel) {
        val contentEntry = rootModel.contentEntries.firstOrNull() ?: return
        val moduleRoot = contentEntry.file ?: return
        val configService = ArendModuleConfigService.getInstance(module) ?: return

        if (isNewModule) {
            VfsUtil.saveText(moduleRoot.findOrCreateChildData(moduleRoot, FileUtils.LIBRARY_CONFIG_FILE),
                buildString {
                    if (langVersionString.isNotEmpty()) appendLine("$LANG_VERSION: $langVersionString")
                    if (versionString.isNotEmpty()) appendLine("$VERSION: $versionString")
                    append("$SOURCES: $sourcesDir")
                    if (testsDir.isNotEmpty()) append("\n$TESTS: $testsDir")
                    if (withBinaries) append("\n$BINARIES: $binariesDirectory")
                    if (withExtensions) append("\n$EXTENSIONS: $extensionsDirectory")
                    if (withExtensions) append("\n$EXTENSION_MAIN: $extensionMainClassData")
                    if (dependencies.isNotEmpty()) append("\n$DEPENDENCIES: ${yamlSeqFromList(dependencies.map { it.name })}")
                })
            configService.copyFrom(this)
        } else {
            if (!runReadAction {
                val yaml = moduleRoot.findChild(FileUtils.LIBRARY_CONFIG_FILE)?.let {
                    PsiManager.getInstance(rootModel.project).findFile(it) as? YAMLFile
                } ?: return@runReadAction false

                configService.copyFromYAML(yaml, false)
                copyFrom(configService)
                return@runReadAction true
            }) {
                return
            }
        }

        val rootPath = FileUtil.toSystemDependentName(moduleRoot.path)

        val srcDir = toAbsolute(rootPath, sourcesDir)
        if (isNewModule) {
            VfsUtil.createDirectories(srcDir)
        }
        contentEntry.addSourceFolder(VfsUtil.pathToUrl(srcDir), false)

        if (testsDir != "") {
            val testDir = toAbsolute(rootPath, testsDir)
            if (isNewModule) {
                VfsUtil.createDirectories(testDir)
            }
            contentEntry.addSourceFolder(VfsUtil.pathToUrl(toAbsolute(rootPath, testDir)), true)
        }

        if (withBinaries) {
            contentEntry.addExcludeFolder(VfsUtil.pathToUrl(toAbsolute(rootPath, binariesDirectory)))
        }

        if (withExtensions) {
            contentEntry.addExcludeFolder(VfsUtil.pathToUrl(toAbsolute(rootPath, extensionsDirectory)))
        }

        val modules = module.project.allModules
        for (otherModule in modules) {
            if (module != otherModule) {
                ArendModuleConfigurationView.getInstance(otherModule)?.updateAvailableLibrariesAndDependencies()
            }
        }
    }
}
