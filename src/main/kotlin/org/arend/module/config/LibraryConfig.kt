package org.arend.module.config

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import org.arend.library.LibraryDependency
import org.arend.library.LibraryManager
import org.arend.mapFirstNotNull
import org.arend.module.ArendRawLibrary
import org.arend.module.ModulePath
import org.arend.psi.ArendFile
import org.arend.util.FileUtils
import java.nio.file.Path
import java.nio.file.Paths


const val DEFAULT_SOURCES_DIR = "src"
const val DEFAULT_OUTPUT_DIR = ".output"

abstract class LibraryConfig {
    open val sourcesDir: String?
        get() = null
    open val outputDir: String?
        get() = null
    open val modules: List<ModulePath>?
        get() = null
    open val dependencies: List<LibraryDependency>
        get() = emptyList()

    abstract val name: String

    abstract val rootPath: Path?

    // Sources directory

    val sourcesPath: Path?
        get() {
            val path = Paths.get(sourcesDir ?: return rootPath)
            return if (path.isAbsolute) path else rootPath?.resolve(path)
        }

    open val sourcesDirFile: VirtualFile?
        get() = sourcesPath?.let { VirtualFileManager.getInstance().getFileSystem(LocalFileSystem.PROTOCOL).findFileByPath(it.toString()) }

    // Output directory

    val outputPath: Path?
        get() {
            val path = Paths.get(outputDir ?: DEFAULT_OUTPUT_DIR)
            return if (path.isAbsolute) path else rootPath?.resolve(path)
        }

    // Modules

    fun findModules(project: Project): List<ModulePath> {
        val modules = modules
        if (modules != null) {
            return modules
        }

        val srcFile = sourcesDirFile
        if (srcFile != null) {
            return getArendFiles(srcFile, project).mapNotNull { it.modulePath }
        }

        val srcPath = sourcesPath
        if (srcPath != null) {
            val list = ArrayList<ModulePath>()
            FileUtils.getModules(srcPath, FileUtils.EXTENSION, list)
            return list
        }

        return emptyList()
    }

    private fun getArendFiles(root: VirtualFile, project: Project): List<ArendFile> {
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

    fun containsModule(modulePath: ModulePath, project: Project): Boolean {
        return modules?.any { it == modulePath } ?: findArendFile(modulePath, project) != null
    }

    fun findArendFilesAndDirectories(modulePath: ModulePath, project: Project): List<PsiFileSystemItem> {
        var dirs = listOf(sourcesDirFile ?: return emptyList())
        val path = modulePath.toList()
        val psiManager = PsiManager.getInstance(project)
        for ((i, name) in path.withIndex()) {
            if (i < path.size - 1) {
                dirs = dirs.mapNotNull { it.findChild(name) }
                if (dirs.isEmpty()) return emptyList()
            } else {
                return dirs.mapNotNull { dir ->
                    val file = dir.findChild(name + FileUtils.EXTENSION)
                    if (file == null) {
                        dir.findChild(name)?.let { psiManager.findDirectory(it) }
                    } else {
                        psiManager.findFile(file) as? ArendFile
                    }
                }
            }
        }
        return emptyList()
    }

    fun findArendFile(modulePath: ModulePath, project: Project): ArendFile? =
        findArendFilesAndDirectories(modulePath, project).filterIsInstance<ArendFile>().firstOrNull()

    // Dependencies

    fun availableConfigs(libraryManager: LibraryManager): List<LibraryConfig> =
        listOf(this) + dependencies.mapNotNull { dep ->
            (libraryManager.getRegisteredLibrary(dep.name) as? ArendRawLibrary)?.config
        }

    inline fun <T> forAvailableConfigs(libraryManager: LibraryManager, f : (LibraryConfig) -> T?): T? =
        f(this) ?: dependencies.mapFirstNotNull { dep ->
            (libraryManager.getRegisteredLibrary(dep.name) as? ArendRawLibrary)?.config?.let { f(it) }
        }
}