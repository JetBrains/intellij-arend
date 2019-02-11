package org.arend.module.config

import org.arend.library.LibraryDependency
import org.arend.module.ModulePath
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence


private fun YAMLFile.getProp(name: String) = (documents?.firstOrNull()?.topLevelValue as? YAMLMapping)?.getKeyValueByKey(name)?.value

val YAMLFile.sourcesDir
    get() = (getProp("sourcesDir") as? YAMLScalar)?.textValue

val YAMLFile.outputDir
    get() = (getProp("outputDir") as? YAMLScalar)?.textValue

val YAMLFile.modules
    get() = (getProp("modules") as? YAMLSequence)?.items?.mapNotNull { item -> (item.value as? YAMLScalar)?.textValue?.let { ModulePath.fromString(it) } }

val YAMLFile.dependencies
    get() = (getProp("dependencies") as? YAMLSequence)?.items?.mapNotNull { item -> (item.value as? YAMLScalar)?.textValue?.let { LibraryDependency(it) } } ?: emptyList()

/* TODO[libraries]
fun YAMLFile.addDependency(libName: String) {
    val depNames = dependencies.map { it.name }
    if (!depNames.contains(libName)) {
        setProp("dependencies", yamlSeqFromList(depNames.plus(libName)))
    }
}

private fun YAMLFile.removeProp(name: String) {
    val mapping = (documents?.firstOrNull()?.topLevelValue as? YAMLMapping)
    CommandProcessor.getInstance().runUndoTransparentAction {
        WriteAction.run<Exception> {
            mapping?.getKeyValueByKey(name)?.let { mapping.deleteKeyValue(it) }
        }
    }
}

private fun yamlSeqFromList(lst: List<String>): String =  "[" + lst.fold("") { acc, x -> if (acc=="") x else "$acc, $x" } + "]"

private fun createFromText(code: String, project: Project): YAMLFile? =
        PsiFileFactory.getInstance(project).createFileFromText("DUMMY.yaml", YAMLFileType.YML, code) as? YAMLFile

private fun YAMLFile.setProp(name: String, value: String) {
    val mapping = (documents?.firstOrNull()?.topLevelValue as? YAMLMapping)
    val dummyMapping = createFromText("$name: $value", project)?.documents?.firstOrNull()?.topLevelValue as? YAMLMapping

    CommandProcessor.getInstance().runUndoTransparentAction {
        WriteAction.run<Exception> {
            dummyMapping?.getKeyValueByKey(name)?.let { mapping?.putKeyValue(it) }
        }
    }
}

var YAMLFile.dependencies: List<LibraryDependency>
    get() = (getProp("dependencies") as? YAMLSequence)?.items?.mapNotNull { (it.value as? YAMLScalar)?.textValue?.let { LibraryDependency(it) } } ?: emptyList()
    set(deps) {
        if (deps.isEmpty()) {
            removeProp("dependencies")
        } else {
            setProp("dependencies", yamlSeqFromList(deps.map { it.name }))
        }
    }
*/
