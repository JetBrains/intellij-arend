package org.vclang.vclpsi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.jetpad.vclang.library.LibraryDependency
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.util.FileUtils
import org.vclang.VclFileType
import org.vclang.VclLanguage
import org.vclang.module.util.getVcFiles
import org.vclang.psi.VcFile
import org.vclang.psi.module
import java.nio.file.Path
import java.nio.file.Paths

class VclFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, VclLanguage.INSTANCE) {
    override fun getFileType(): FileType = VclFileType

    companion object {
        fun getDefaultBinPath(module: Module): Path? {
            return module.moduleFilePath.let {Paths.get(FileUtil.toSystemDependentName(it)).resolveSibling(".output")}
        }
    }

    val sourcesDirPath: Path?
        get() {
            var path: Path? = null
            PsiTreeUtil.processElements(this) { it.accept(object: VclVisitor() {
                override fun visitSourceSt(sourceSt: VclSourceSt) {
                    path = sourceSt.node.getChildren(TokenSet.create(VclElementTypes.DIRNAME)).firstOrNull()?.text.let {
                        Paths.get(it)
                    }
                }
            }); true }
            return path
        }

    val binariesDirPath: Path?
        get() {
            var path: Path? = null
            PsiTreeUtil.processElements(this) { it.accept(object: VclVisitor() {
                override fun visitBinarySt(binarySt: VclBinarySt) {
                    path = binarySt.node.getChildren(TokenSet.create(VclElementTypes.DIRNAME)).firstOrNull()?.text.let {
                        Paths.get(it)
                    }
                }
            }); true }
            return path ?: module?.let { getDefaultBinPath(it) }
        }

    val sourcesDir: VirtualFile?
        get() {
            val url = sourcesDirPath?.let { VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL,
                    it.toString()) } ?: return null
            return VirtualFileManager.getInstance().findFileByUrl(url)
        }

    val libModules: List<ModulePath>
        get() {
            var modules = module?.getVcFiles(sourcesDir) ?: emptyList()
            PsiTreeUtil.processElements(this) { it.accept(object: VclVisitor() {
                override fun visitModulesSt(modulesSt: VclModulesSt) {
                    val modPaths = modulesSt.node.getChildren(TokenSet.create(VclElementTypes.MODNAME)).map { it.text }
                    modules = modules.filter { modPaths.contains(it.toString()) }
                }
            }); true }
            return modules.map { it.modulePath }
        }

    val dependencies: List<LibraryDependency>
        get() {
            var deps = emptyList<LibraryDependency>()
            PsiTreeUtil.processElements(this) { it.accept(object: VclVisitor() {
                override fun visitDepsSt(depsSt: VclDepsSt) {
                    deps = depsSt.node.getChildren(TokenSet.create(VclElementTypes.LIBNAME)).map { LibraryDependency(it.text) }
                }
            }); true }
            return deps
        }

    fun findVcFilesAndDirectories(modulePath: ModulePath): List<PsiFileSystemItem> {
        val dir = sourcesDir ?: parent?.virtualFile ?: return emptyList()
        var dirs = listOf(dir)
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

    fun findVcFiles(modulePath: ModulePath): List<VcFile> =
            runReadAction { findVcFilesAndDirectories(modulePath) }.filterIsInstance<VcFile>()
}