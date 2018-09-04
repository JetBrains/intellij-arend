package org.vclang.vclpsi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.jetbrains.jetpad.vclang.library.LibraryDependency
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.util.FileUtils
import org.vclang.VclFileType
import org.vclang.VclLanguage
import org.vclang.module.util.defaultRoot
import org.vclang.psi.VcFile
import org.vclang.psi.module
import java.nio.file.Path
import java.nio.file.Paths

class VclFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, VclLanguage.INSTANCE) {
    override fun getFileType(): FileType = VclFileType

    private val moduleBasePath: Path?
        get() = module?.let { Paths.get(FileUtil.toSystemDependentName(it.moduleFilePath)) }

    val sourcesDir: String?
        get() {
            val root = module?.defaultRoot?.path
            val dir = children.filterIsInstance<VclStatement>().mapNotNull { it.sourceStat }.firstOrNull()?.directoryName?.text ?: return root
            return when {
                root != null -> Paths.get(root).resolve(dir).toString()
                Paths.get(dir).isAbsolute -> dir
                else -> null
            }
        }

    val binariesPath: Path?
        get() {
            val path = Paths.get(children.filterIsInstance<VclStatement>().mapNotNull { it.binaryStat }.firstOrNull()?.directoryName?.text ?: ".output")
            return if (path.isAbsolute) path else moduleBasePath?.resolveSibling(path)
        }

    val sourcesDirFile: VirtualFile?
        get() {
            val root = module?.defaultRoot
            val stat = children.filterIsInstance<VclStatement>().map { it.sourceStat }.firstOrNull() ?: return root
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
            val moduleStat = (it as? VclStatement)?.modulesStat
            if (moduleStat != null) {
                found = true
                moduleStat.modNameList.any { it.moduleName.text == moduleStr }
            } else {
                false
            }
        } || !found && findVcFile(modulePath) != null
    }

    val libModules: List<ModulePath>
        get() = children.flatMap { (it as? VclStatement)?.modulesStat?.modNameList?.map { ModulePath.fromString(it.moduleName.text) } ?: emptyList() }

    val dependencies: List<LibraryDependency>
        get() = children.flatMap { (it as? VclStatement)?.depsStat?.libNameList?.map { LibraryDependency(it.text) } ?: emptyList() }

    fun findVcFilesAndDirectories(modulePath: ModulePath): List<PsiFileSystemItem> {
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
                        psiManager.findFile(file) as? VcFile
                    }
                }
            }
        }
        return emptyList()
    }

    fun findVcFile(modulePath: ModulePath): VcFile? =
        findVcFilesAndDirectories(modulePath).filterIsInstance<VcFile>().firstOrNull()
}