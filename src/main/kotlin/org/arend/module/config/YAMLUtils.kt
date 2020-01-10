package org.arend.module.config

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import org.arend.ext.module.ModulePath
import org.arend.library.LibraryDependency
import org.arend.psi.getLibraryConfig
import org.arend.util.FileUtils
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence


const val SOURCES = "sourcesDir"
const val BINARIES = "binariesDir"
const val EXTENSIONS = "extensionsDir"
const val EXTENSION_MAIN = "extensionMainClass"
const val MODULES = "modules"
const val DEPENDENCIES = "dependencies"
const val LANG_VERSION = "langVersion"

val KEYS = setOf(SOURCES, BINARIES, EXTENSIONS, EXTENSION_MAIN, MODULES, DEPENDENCIES, LANG_VERSION)

private fun YAMLFile.getProp(name: String) = (documents?.firstOrNull()?.topLevelValue as? YAMLMapping)?.getKeyValueByKey(name)?.value

fun yamlSeqFromList(lst: List<String>): String =  "[" + lst.reduce { acc, x -> "$acc, $x" } + "]"

private fun createFromText(code: String, project: Project): YAMLFile? =
    PsiFileFactory.getInstance(project).createFileFromText("DUMMY.yaml", YAMLFileType.YML, code) as? YAMLFile

private fun YAMLFile.setProp(name: String, value: String?) {
    val mapping = documents?.firstOrNull()?.topLevelValue as? YAMLMapping ?: return
    if (value == null) {
        val keyValue = mapping.getKeyValueByKey(name) ?: return
        mapping.deleteKeyValue(keyValue)
    } else {
        val fixedValue = if (value.isEmpty()) "\"\"" else value
        val keyValue = (createFromText("$name: $fixedValue", project)?.documents?.firstOrNull()?.topLevelValue as? YAMLMapping)?.getKeyValueByKey(name) ?: return
        mapping.putKeyValue(keyValue)
    }
}

var YAMLFile.sourcesDir
    get() = (getProp(SOURCES) as? YAMLScalar)?.textValue
    set(value) {
        setProp(SOURCES, value)
    }

var YAMLFile.binariesDir
    get() = (getProp(BINARIES) as? YAMLScalar)?.textValue
    set(value) {
        setProp(BINARIES, value)
    }

var YAMLFile.extensionsDir
    get() = (getProp(EXTENSIONS) as? YAMLScalar)?.textValue
    set(value) {
        setProp(EXTENSIONS, value)
    }

var YAMLFile.extensionMainClass
    get() = (getProp(EXTENSION_MAIN) as? YAMLScalar)?.textValue
    set(value) {
        setProp(EXTENSION_MAIN, value)
    }

val YAMLFile.modules
    get() = (getProp(MODULES) as? YAMLSequence)?.items?.mapNotNull { item -> (item.value as? YAMLScalar)?.textValue?.let { ModulePath.fromString(it) } }

var YAMLFile.dependencies
    get() = (getProp(DEPENDENCIES) as? YAMLSequence)?.items?.mapNotNull { item -> (item.value as? YAMLScalar)?.textValue?.let { LibraryDependency(it) } } ?: emptyList()
    set(deps) {
        if (deps.isEmpty()) {
            if ((getProp(DEPENDENCIES) as? YAMLSequence)?.items?.isEmpty() == false) {
                setProp(DEPENDENCIES, "[]")
            }
        } else {
            setProp(DEPENDENCIES, yamlSeqFromList(deps.map { it.name }))
        }
    }

var YAMLFile.langVersion
    get() = (getProp(LANG_VERSION) as? YAMLScalar)?.textValue
    set(value) {
        setProp(LANG_VERSION, value)
    }

fun YAMLFile.write(block: YAMLFile.() -> Unit) {
    ApplicationManager.getApplication().invokeLater { runUndoTransparentWriteAction {
        block()
    } }
}

val PsiFile.isYAMLConfig: Boolean
    get() {
        if (this !is YAMLFile) {
            return false
        }
        if (name != FileUtils.LIBRARY_CONFIG_FILE) {
            return false
        }

        val rootPath = getLibraryConfig(true)?.rootPath ?: return false
        return virtualFile.parent.path == FileUtil.toSystemIndependentName(rootPath.toString())
    }
