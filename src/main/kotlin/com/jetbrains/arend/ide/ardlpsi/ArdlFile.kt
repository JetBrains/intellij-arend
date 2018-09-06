package com.jetbrains.arend.ide.ardlpsi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.jetbrains.arend.ide.ArdlFileType
import com.jetbrains.arend.ide.module.util.defaultRoot
import com.jetbrains.arend.ide.psi.ArdFile
import com.jetbrains.arend.ide.psi.module
import com.jetbrains.jetpad.vclang.library.LibraryDependency
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.util.FileUtils
import java.nio.file.Path
import java.nio.file.Paths

class ArdlFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, com.jetbrains.arend.ide.ArdlLanguage.INSTANCE) {
    override fun getFileType(): FileType = ArdlFileType

    private val moduleBasePath: Path?
        get() = module?.let { Paths.get(FileUtil.toSystemDependentName(it.moduleFilePath)) }

    val sourcesDir: String?
        get() {
            val root = module?.defaultRoot?.path
            val dir = children.filterIsInstance<ArdlStatement>().mapNotNull { it.sourceStat }.firstOrNull()?.directoryName?.text
                    ?: return root
            return when {
                root != null -> Paths.get(root).resolve(dir).toString()
                Paths.get(dir).isAbsolute -> dir
                else -> null
            }
        }

    val binariesPath: Path?
        get() {
            val path = Paths.get(children.filterIsInstance<ArdlStatement>().mapNotNull { it.binaryStat }.firstOrNull()?.directoryName?.text
                    ?: ".output")
            return if (path.isAbsolute) path else moduleBasePath?.resolveSibling(path)
        }

    val sourcesDirFile: VirtualFile?
        get() {
            val root = module?.defaultRoot
            val stat = children.filterIsInstance<ArdlStatement>().map { it.sourceStat }.firstOrNull() ?: return root
            val dir = stat.directoryName?.text ?: return null
            val path = when {
                root != null -> Paths.get(root.path).resolve(dir).toString()
                Paths.get(dir).isAbsolute -> dir
                else -> return null
            }
            return VirtualFileManager.getInstance().getFileSystem(LocalFileSystem.PROTOCOL).findFileByPath(path)
        }

    fun containsModule(modulePath: ModulePath): Boolean {
        val moduleStr = modulePath.toString()
        var found = false
        return children.any {
            val moduleStat = (it as? ArdlStatement)?.modulesStat
            if (moduleStat != null) {
                found = true
                moduleStat.modNameList.any { it.moduleName.text == moduleStr }
            } else {
                false
            }
        } || !found && findArdFile(modulePath) != null
    }

    val libModules: List<ModulePath>
        get() {
            val result = children.flatMap {
                (it as? ArdlStatement)?.modulesStat?.modNameList?.map { ModulePath.fromString(it.moduleName.text) }
                        ?: emptyList()
            }
            return if (!result.isEmpty()) result else sourcesDirFile?.let { getArdFiles(it).map { it.modulePath } }
                    ?: emptyList()
        }

    private fun getArdFiles(root: VirtualFile): List<ArdFile> {
        val result = ArrayList<ArdFile>()
        val psiManager = PsiManager.getInstance(project)
        VfsUtilCore.iterateChildrenRecursively(root, null) { file ->
            if (file.name.endsWith(FileUtils.EXTENSION)) {
                (psiManager.findFile(file) as? ArdFile)?.let { result.add(it) }
            }
            return@iterateChildrenRecursively true
        }
        return result
    }

    val dependencies: List<LibraryDependency>
        get() = children.flatMap {
            (it as? ArdlStatement)?.depsStat?.libNameList?.map { LibraryDependency(it.text) } ?: emptyList()
        }

    fun findArdFilesAndDirectories(modulePath: ModulePath): List<PsiFileSystemItem> {
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
                        psiManager.findFile(file) as? ArdFile
                    }
                }
            }
        }
        return emptyList()
    }

    fun findArdFile(modulePath: ModulePath): ArdFile? =
            findArdFilesAndDirectories(modulePath).filterIsInstance<ArdFile>().firstOrNull()
}