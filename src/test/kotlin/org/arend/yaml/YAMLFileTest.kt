package org.arend.yaml

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleUtilCore
import org.arend.ArendTestBase
import org.arend.module.config.ArendModuleConfigService
import org.arend.util.FileUtils.LIBRARY_CONFIG_FILE
import org.jetbrains.yaml.YAMLFileType

class YAMLFileTest : ArendTestBase() {
    override var dataPath = "org/arend/yaml"

    fun testDir() {
        val yamlFileService = project.service<YamlFileService>()
        val yamlFile = myFixture.configureByText(YAMLFileType.YML, """
            langVersion: 1.10
            sourcesDir: src
            dependencies: []
            binariesDir: bin<caret>
        """.trimIndent())
        runWriteAction {
            yamlFile.name = LIBRARY_CONFIG_FILE
        }

        val file = yamlFile.virtualFile
        yamlFileService.removeChangedFile(file)
        yamlFileService.clearSameFields()

        val module = ModuleUtilCore.findModuleForFile(file, project)
        val arendModuleConfigService = ArendModuleConfigService.getInstance(module)
        yamlFileService.updateIdea(file, arendModuleConfigService)
        myFixture.type("/")

        assertTrue(!yamlFileService.containsChangedFile(file))

        myFixture.type("abc")
        assertTrue(yamlFileService.containsChangedFile(file))
    }

    fun testList() {
        val yamlFileService = project.service<YamlFileService>()
        val yamlFile = myFixture.configureByText(YAMLFileType.YML, """
            langVersion: 1.10
            sourcesDir: src
            dependencies: []
            binariesDir: bin
        """.trimIndent())
        runWriteAction {
            yamlFile.name = LIBRARY_CONFIG_FILE
        }

        val file = yamlFile.virtualFile
        yamlFileService.removeChangedFile(file)
        yamlFileService.clearSameFields()

        val module = ModuleUtilCore.findModuleForFile(file, project)
        val arendModuleConfigService = ArendModuleConfigService.getInstance(module)
        yamlFileService.updateIdea(file, arendModuleConfigService)
        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.deleteString(33, 50)
        }

        assertTrue(!yamlFileService.containsChangedFile(file))
    }

    fun testSameFields() {
        val yamlFileService = project.service<YamlFileService>()
        val yamlFile = myFixture.configureByText(YAMLFileType.YML, """
            langVersion: 1.10
            sourcesDir: src
            dependencies: []
            binariesDir: bin
        """.trimIndent())
        runWriteAction {
            yamlFile.name = LIBRARY_CONFIG_FILE
        }

        val file = yamlFile.virtualFile
        yamlFileService.removeChangedFile(file)
        yamlFileService.clearSameFields()

        val module = ModuleUtilCore.findModuleForFile(file, project)
        val arendModuleConfigService = ArendModuleConfigService.getInstance(module)
        yamlFileService.updateIdea(file, arendModuleConfigService)

        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.deleteString(64, 67)
            myFixture.editor.document.insertString(64, "src")
        }

        assertTrue(yamlFileService.getSameFields().isNotEmpty())

        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.deleteString(64, 67)
            myFixture.editor.document.insertString(64, "bin")
        }

        assertTrue(yamlFileService.getSameFields().isEmpty())
    }
}
