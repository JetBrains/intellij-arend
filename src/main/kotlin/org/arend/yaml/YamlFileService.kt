package org.arend.yaml

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.intellij.psi.PsiManager
import com.intellij.util.containers.ContainerUtil
import org.arend.actions.mark.DirectoryType
import org.arend.actions.mark.DirectoryType.*
import org.arend.actions.mark.addMarkedDirectory
import org.arend.actions.mark.commitModel
import org.arend.actions.mark.removeMarkedDirectory
import org.arend.module.config.ArendModuleConfigService
import org.arend.util.VersionRange
import org.jetbrains.yaml.psi.YAMLFile

@Service(Service.Level.PROJECT)
class YamlFileService(private val project: Project) {

    private val changedFiles = ContainerUtil.newConcurrentSet<VirtualFile>()
    private val sameFields = ContainerUtil.newConcurrentSet<String>()

    fun containsChangedFile(file: VirtualFile): Boolean {
        return changedFiles.contains(file)
    }

    private fun addChangedFile(file: VirtualFile) {
        changedFiles.add(file)
    }

    fun removeChangedFile(file: VirtualFile) {
        changedFiles.remove(file)
    }

    fun getSameFields(): Set<String> {
        return sameFields
    }

    private fun addSameFields(fields: List<String>) {
        sameFields.addAll(fields)
    }

    fun clearSameFields() {
        sameFields.clear()
    }

    fun checkSameFields(file: VirtualFile, text: String = file.readText()): Boolean {
        clearSameFields()

        val newYamlFile = createFromText(text, project)

        val newSrc = newYamlFile?.sourcesDir.orEmpty().trim('/')
        val newTest = newYamlFile?.testsDir.orEmpty().trim('/')
        val newBin = newYamlFile?.binariesDir.orEmpty().trim('/')

        val newDirs = listOf(Pair(newSrc, SOURCES), Pair(newTest, TESTS), Pair(newBin, BINARIES))
        val compareResult = compareMainDirs(newDirs.map { it.first })
        if (compareResult.first) {
            addSameFields(compareResult.second.map { newDirs[it].second })
        }
        return compareResult.first
    }

    private fun compareMainDirs(newDirs: List<String>): Pair<Boolean, List<Int>> {
        if (newDirs.size == 3 && newDirs[0] == newDirs[1] && newDirs[1] == newDirs[2] && newDirs[2] != "") {
            return Pair(true, listOf(0, 1, 2))
        }
        for (firstIndex in newDirs.indices) {
            for (secondIndex in newDirs.indices) {
                if (firstIndex != secondIndex && newDirs[firstIndex] != "" && newDirs[firstIndex] == newDirs[secondIndex]) {
                    return Pair(true, listOf(firstIndex, secondIndex))
                }
            }
        }
        return Pair(false, emptyList())
    }

    fun compareSettings(file: VirtualFile, text: String = file.readText()): Boolean {
        val newYamlFile = createFromText(text, project)
        val arendModuleConfigService = ArendModuleConfigService.getInstance(ModuleUtil.findModuleForFile(file, project))
        val updateFlag = arendModuleConfigService?.sourcesDir != newYamlFile?.sourcesDir.orEmpty().trim('/') ||
                arendModuleConfigService.testsDir != newYamlFile?.testsDir.orEmpty().trim('/') ||
                arendModuleConfigService.binariesDirectory != newYamlFile?.binariesDir.orEmpty().trim('/') ||
                arendModuleConfigService.extensionsDirectory != newYamlFile?.extensionsDir.orEmpty().trim('/') ||
                arendModuleConfigService.extensionMainClassData != newYamlFile?.extensionMainClass.orEmpty() ||
                arendModuleConfigService.modules.orEmpty().sorted() != newYamlFile?.modules.orEmpty().sorted() ||
                arendModuleConfigService.dependencies.sorted() != newYamlFile?.dependencies.orEmpty().sorted() ||
                VersionRange.parseVersionRange(arendModuleConfigService.versionString) != VersionRange.parseVersionRange(newYamlFile?.version.orEmpty()) ||
                VersionRange.parseVersionRange(arendModuleConfigService.langVersionString) != VersionRange.parseVersionRange(newYamlFile?.langVersion.orEmpty())
        if (updateFlag) {
            addChangedFile(file)
        } else {
            removeChangedFile(file)
        }
        return updateFlag
    }

    fun updateIdea(yamlVirtualFile: VirtualFile, arendModuleConfigService: ArendModuleConfigService?) {
        val yaml = PsiManager.getInstance(project).findFile(yamlVirtualFile) as? YAMLFile ?: return
        arendModuleConfigService?.let { updateDirectories(yaml, yamlVirtualFile, it) }
    }

    private fun updateDirectories(yaml: YAMLFile, file: VirtualFile, arendModuleConfigService: ArendModuleConfigService) {
        val model = ModuleRootManager.getInstance(arendModuleConfigService.module).modifiableModel
        val yamlSrc = runReadAction {
            yaml.sourcesDir
        }
        val yamlBin = runReadAction {
            val bin = yaml.binariesDir
            if (yamlSrc == bin) {
                runUndoTransparentWriteAction {
                    yaml.binariesDir = ""
                }
                ""
            } else {
                bin
            }
        }
        val yamlTest = runReadAction {
            val test = yaml.testsDir
            if (yamlSrc == test) {
                runUndoTransparentWriteAction {
                    yaml.testsDir = ""
                }
                ""
            } else if (yamlBin == test) {
                runUndoTransparentWriteAction {
                    yaml.testsDir = ""
                }
                ""
            } else {
                test
            }
        }

        removeDirectory(model, arendModuleConfigService, yamlSrc, arendModuleConfigService.sourcesDir, file, SRC)
        removeDirectory(model, arendModuleConfigService, yamlTest, arendModuleConfigService.testsDir, file, TEST_SRC)
        removeDirectory(model, arendModuleConfigService, yamlBin, arendModuleConfigService.binariesDirectory, file, BIN)

        addDirectory(model, arendModuleConfigService, yamlSrc, arendModuleConfigService.sourcesDir, SRC)
        addDirectory(model, arendModuleConfigService, yamlTest, arendModuleConfigService.testsDir, TEST_SRC)
        addDirectory(model, arendModuleConfigService, yamlBin, arendModuleConfigService.binariesDirectory, BIN)

        commitModel(arendModuleConfigService.module, model)
    }

    private fun removeDirectory(model: ModifiableRootModel?, arendModuleConfigService: ArendModuleConfigService, yamlDir: String?, configDir: String?, file: VirtualFile, directoryType: DirectoryType) {
        if (yamlDir != configDir) {
            removeMarkedDirectory(file, model, arendModuleConfigService, directoryType)
        }
    }

    private fun addDirectory(model: ModifiableRootModel?, arendModuleConfigService: ArendModuleConfigService, yamlDir: String?, configDir: String?, directoryType: DirectoryType) {
        if (yamlDir != configDir) {
            addMarkedDirectory(yamlDir, model, arendModuleConfigService, directoryType)
        }
    }
}
