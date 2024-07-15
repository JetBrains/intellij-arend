package org.arend.yaml

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.util.containers.ContainerUtil
import org.arend.actions.mark.DirectoryType
import org.arend.actions.mark.DirectoryType.*
import org.arend.actions.mark.addMarkedDirectory
import org.arend.actions.mark.removeMarkedDirectory
import org.arend.module.config.ArendModuleConfigService
import org.jetbrains.yaml.psi.YAMLFile

class YamlFileService(private val project: Project) {

    private val changedFiles = ContainerUtil.newConcurrentSet<VirtualFile>()

    fun containsChangedFile(file: VirtualFile): Boolean {
        return changedFiles.contains(file)
    }

    fun addChangedFile(file: VirtualFile) {
        changedFiles.add(file)
    }

    fun removeChangedFile(file: VirtualFile) {
        changedFiles.remove(file)
    }

    fun updateIdea(yamlVirtualFile: VirtualFile) {
        val yaml = PsiManager.getInstance(project).findFile(yamlVirtualFile) as? YAMLFile ?: return
        var module: Module? = null
        ApplicationManager.getApplication().executeOnPooledThread {
            module = ModuleUtilCore.findModuleForFile(yamlVirtualFile, project)
        }.get()
        val arendModuleConfigService = ArendModuleConfigService.getInstance(module) ?: return

        updateDirectories(yaml, yamlVirtualFile, arendModuleConfigService)

        ApplicationManager.getApplication().run {
            executeOnPooledThread {
                runReadAction {
                    arendModuleConfigService.copyFromYAML(true)
                }
            }
        }
    }

    private fun updateDirectories(yaml: YAMLFile, file: VirtualFile, arendModuleConfigService: ArendModuleConfigService) {
        removeDirectory(arendModuleConfigService, yaml.sourcesDir, arendModuleConfigService.sourcesDir, file, SRC)
        removeDirectory(arendModuleConfigService, yaml.testsDir, arendModuleConfigService.testsDir, file, TEST_SRC)
        removeDirectory(arendModuleConfigService, yaml.binariesDir, arendModuleConfigService.binariesDirectory, file, BIN)

        addDirectory(arendModuleConfigService, yaml.sourcesDir, arendModuleConfigService.sourcesDir, SRC)
        addDirectory(arendModuleConfigService, yaml.testsDir, arendModuleConfigService.testsDir, TEST_SRC)
        addDirectory(arendModuleConfigService, yaml.binariesDir, arendModuleConfigService.binariesDirectory, BIN)
    }

    private fun removeDirectory(arendModuleConfigService: ArendModuleConfigService, yamlDir: String?, configDir: String?, file: VirtualFile, directoryType: DirectoryType) {
        if (yamlDir != configDir) {
            removeMarkedDirectory(file, arendModuleConfigService, directoryType)
        }
    }

    private fun addDirectory(arendModuleConfigService: ArendModuleConfigService, yamlDir: String?, configDir: String?, directoryType: DirectoryType) {
        if (yamlDir != configDir) {
            addMarkedDirectory(yamlDir, arendModuleConfigService, directoryType)
        }
    }
}
