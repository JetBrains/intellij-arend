package org.arend.module.util

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import org.arend.arendModules
import org.arend.library.LibraryDependency
import org.arend.module.ArendRawLibrary
import org.arend.module.ModulePath
import org.arend.psi.ArendFile
import org.arend.psi.module
import org.arend.typechecking.TypeCheckingService
import org.arend.util.FileUtils
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import java.nio.file.Path
import java.nio.file.Paths

fun toAbsolute(root: String, path: String): String = if (FileUtil.isAbsolute(path)) path else FileUtil.join(root, path)

fun toRelative(root: String, path: String): String? {
    if (FileUtil.isAbsolute(path)) {
        if (!path.startsWith(root)) return null
        return path.substring(root.length + 1)
    }
    return path
}

val DEFAULT_OUTPUT = ".output"

fun findExternalLibrariesInDirectory(path: Path): List<Path> {
    val dir = path.toFile()
    val res = mutableListOf<Path>()

    for (subDir in dir.listFiles()) {
        if (subDir.listFiles()?.find { it.name == "arend.yaml" } != null) {
            res.add(Paths.get(subDir.path))
        }
    }

    return res.toList()
    // return dir?.walkTopDown()?.filter { it.name == "arend.yaml" }?.map { Paths.get(it.parentFile.path) }?.toList() ?: emptyList()
}

fun getProjectDependencies(project: Project): List<LibraryDependency> {
    val res = mutableListOf<LibraryDependency>()

    for (module in project.arendModules) {
        module.libraryConfig?.dependencies?.let { res.addAll(it) }
    }

    return res.toList()
}

fun findLibHeaderInDirectory(path: Path, libName: String): Path? {
    val dir = path.toFile()
    return dir?.walkTopDown()?.find { it.name == "arend.yaml" && it.parentFile.name == libName }?.toPath()
}

fun findLibHeader(project: Project, libName: String): Path? {
    val libsHome = ProjectRootManager.getInstance(project).projectSdk?.homePath
    return libsHome?.let { findLibHeaderInDirectory(Paths.get(it), libName)}
}

fun libHeaderByPath(path: Path, project: Project): YAMLFile? {
    val vfile = VirtualFileManager.getInstance().getFileSystem(LocalFileSystem.PROTOCOL).findFileByPath(path.toString()) ?: return null
    return PsiManager.getInstance(project).findFile(vfile) as? YAMLFile
}

fun YAMLFile.addDependency(libName: String) {
    val depNames = dependencies.map { it.name }
    if (!depNames.contains(libName)) {
        setProp("dependencies", yamlSeqFromList(depNames.plus(libName)))
    }
}

val Module.defaultRoot: VirtualFile?
    get() = ModuleRootManager.getInstance(this).contentEntries.firstOrNull()?.file

private val YAMLFile.moduleBasePath: Path?
    get() = module?.let { Paths.get(FileUtil.toSystemDependentName(it.moduleFilePath)) }

private fun YAMLFile.getProp(name: String) = (documents?.firstOrNull()?.topLevelValue as? YAMLMapping)?.getKeyValueByKey(name)?.value

private fun yamlSeqFromList(lst: List<String>): String =  "[" + lst.fold("") { acc, x -> if (acc=="") x else "$acc, $x" } + "]"

private fun createFromText(code: String, project: Project): YAMLFile? =
        PsiFileFactory.getInstance(project).createFileFromText("DUMMY.yaml", YAMLFileType.YML, code) as? YAMLFile

private fun YAMLFile.setProp(name: String, value: String) {
    val mapping = (documents?.firstOrNull()?.topLevelValue as? YAMLMapping)
    val dummyMapping = createFromText("${name}: ${value}", project)?.documents?.firstOrNull()?.topLevelValue as? YAMLMapping

    //CommandProcessor.getInstance().runUndoTransparentAction {

    CommandProcessor.getInstance().runUndoTransparentAction {
        WriteAction.run<Exception> {
            dummyMapping?.getKeyValueByKey(name)?.let { mapping?.putKeyValue(it) }
        }
        //mapping?.getKeyValueByKey(name)?.let { mapping.deleteKeyValue(it) }
    }

}

val YAMLFile.libraryPath: Path?
    get() = parent?.virtualFile?.path.let { Paths.get(it) }

val YAMLFile.sourcesDirProp: String?
    get() = (getProp("sourcesDir") as? YAMLScalar)?.textValue

val YAMLFile.outputDirProp: String?
    get() = (getProp("outputDir") as? YAMLScalar)?.textValue

val YAMLFile.outputPath: Path?
    get() {
        val path = Paths.get((getProp("outputDir") as? YAMLScalar)?.textValue ?: DEFAULT_OUTPUT)
        return if (path.isAbsolute) path else libraryPath?.resolve(path)
    }

val YAMLFile.libName: String?
    get() = libraryPath?.fileName.toString()

val YAMLFile.sourcesDirPath: Path?
    get() {
        val path = Paths.get((getProp("sourcesDir") as? YAMLScalar)?.textValue ?: return null)
        return if (path.isAbsolute) path else libraryPath?.resolve(path)
    }

val YAMLFile.libModulesProp: List<String>?
    get() = (getProp("modules") as? YAMLSequence)?.items?.mapNotNull { (it.value as? YAMLScalar)?.textValue }

val YAMLFile.libModules: List<ModulePath>
    get() = libModulesProp?.mapNotNull { FileUtils.modulePath(it) } ?: sourcesDirFile?.let { getArendFiles(it).mapNotNull { it.modulePath } } ?: emptyList()

private fun YAMLFile.getArendFiles(root: VirtualFile): List<ArendFile> {
    val result = ArrayList<ArendFile>()
    val psiManager = PsiManager.getInstance(project)
    VfsUtilCore.iterateChildrenRecursively(root, null) { file ->
        if (file.name.endsWith(FileUtils.EXTENSION)) {
            (psiManager.findFile(file) as? ArendFile)?.let { result.add(it) }
        }
        return@iterateChildrenRecursively true
    }
    return result
}

var YAMLFile.dependencies: List<LibraryDependency>
    get() = (getProp("dependencies") as? YAMLSequence)?.items?.mapNotNull { (it.value as? YAMLScalar)?.textValue?.let { LibraryDependency(it) } } ?: emptyList()
    set(deps) {
        setProp("dependencies", yamlSeqFromList(deps.map { it.name }))
    }

val YAMLFile.dependencyConfigs: List<YAMLFile>
    get() {
        val libraryManager = TypeCheckingService.getInstance(project).libraryManager
        return dependencies.mapNotNull { (libraryManager.getRegisteredLibrary(it.name) as? ArendRawLibrary)?.headerFile }
    }

val YAMLFile.availableConfigs
    get() = listOf(this) + dependencyConfigs

val Module.sourcesDir: String?
    get() {
        val root = defaultRoot?.path
        val dir = libraryConfig?.sourcesDirProp ?: return root
        return when {
            root != null -> Paths.get(root).resolve(dir).toString()
            Paths.get(dir).isAbsolute -> dir
            else -> null
        }
    }

val YAMLFile.sourcesDirFile: VirtualFile?
    get() {
        val root = module?.defaultRoot
        val dir = sourcesDirProp ?: return root
        val path = when {
            root != null -> Paths.get(root.path).resolve(dir).toString()
            Paths.get(dir).isAbsolute -> dir
            else -> return null
        }
        return VirtualFileManager.getInstance().getFileSystem(LocalFileSystem.PROTOCOL).findFileByPath(path)
    }

fun YAMLFile.containsModule(modulePath: ModulePath): Boolean {
    val moduleStr = modulePath.toString()
    return libModulesProp?.any { it == moduleStr } ?: findArendFile(modulePath) != null
}

fun YAMLFile.findArendFilesAndDirectories(modulePath: ModulePath): List<PsiFileSystemItem> {
    var dirs = sourcesDirFile?.let { listOf(it) } ?: return emptyList()
    val path = modulePath.toList()
    val psiManager = PsiManager.getInstance(project)
    for ((i, name) in path.withIndex()) {
        if (i < path.size - 1) {
            dirs = dirs.mapNotNull { it.findChild(name) }
            if (dirs.isEmpty()) return emptyList()
        } else {
            return dirs.mapNotNull {
                val file = it.findChild(name + FileUtils.EXTENSION)
                if (file == null) {
                    it.findChild(name)?.let { psiManager.findDirectory(it) }
                } else {
                    psiManager.findFile(file) as? ArendFile
                }
            }
        }
    }
    return emptyList()
}

fun YAMLFile.findArendFile(modulePath: ModulePath): ArendFile? =
    findArendFilesAndDirectories(modulePath).filterIsInstance<ArendFile>().firstOrNull()

val Module.libraryConfig: YAMLFile?
    get() {
        val virtualFile = defaultRoot?.findChild(FileUtils.LIBRARY_CONFIG_FILE) ?: return null
        return PsiManager.getInstance(project).findFile(virtualFile) as? YAMLFile
    }

val Module.isArendModule: Boolean
    get() = libraryConfig != null
