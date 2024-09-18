package org.arend.yaml

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import org.arend.ArendTestBase
import java.io.File

class YAMLFileTest : ArendTestBase() {
    override var dataPath = "org/arend/yaml"

    fun testDir() {
        File("$testDataPath/arend.yaml").writeText("""
            langVersion: 1.10
            sourcesDir: src
            dependencies: []
            binariesDir: bin<caret>
        """.trimIndent())

        val yamlFileService = project.service<YamlFileService>()
        val yamlFile = myFixture.configureByFile("arend.yaml")

        yamlFileService.removeChangedFile(yamlFile.virtualFile)
        yamlFileService.updateIdea(yamlFile.virtualFile)
        myFixture.type("/")

        assertTrue(!yamlFileService.containsChangedFile(yamlFile.virtualFile))

        myFixture.type("abc")
        assertTrue(yamlFileService.containsChangedFile(yamlFile.virtualFile))
    }

    fun testList() {
        File("$testDataPath/arend.yaml").writeText("""
            langVersion: 1.10
            sourcesDir: src
            dependencies: []
            binariesDir: bin
        """.trimIndent())

        val yamlFileService = project.service<YamlFileService>()
        val yamlFile = myFixture.configureByFile("arend.yaml")

        yamlFileService.removeChangedFile(yamlFile.virtualFile)
        yamlFileService.updateIdea(yamlFile.virtualFile)
        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.deleteString(33, 50)
        }

        assertTrue(!yamlFileService.containsChangedFile(yamlFile.virtualFile))
    }
}
