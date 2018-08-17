package org.vclang.module.util

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.util.FileUtils
import org.vclang.module.VcRawLibrary
import org.vclang.psi.VcFile
import org.vclang.typechecking.TypeCheckingService

val Module.roots: Array<VirtualFile>
    get() = ModuleRootManager.getInstance(this).sourceRoots.let { if (it.isEmpty()) ModuleRootManager.getInstance(this).contentRoots else it }

fun Module.getVcFiles(dir: VirtualFile?): List<VcFile> {
        val dirs = dir?.let { arrayOf(it) } ?: roots
        val result = ArrayList<VcFile>()
        val psiManager = PsiManager.getInstance(project)
        for (dir_ in dirs) {
            VfsUtilCore.iterateChildrenRecursively(dir_, null, { file ->
                if (file.name.endsWith(FileUtils.EXTENSION)) {
                    (psiManager.findFile(file) as? VcFile)?.let { result.add(it) }
                }
                return@iterateChildrenRecursively true
            })
        }
        return result
    }

val Module.containsVcFile: Boolean
    get() {
        var found = false
        for (root in roots) {
            VfsUtilCore.iterateChildrenRecursively(root, null, { file ->
                if (file.name.endsWith(FileUtils.EXTENSION)) {
                    found = true
                    return@iterateChildrenRecursively false
                } else {
                    return@iterateChildrenRecursively true
                }
            })
            if (found) break
        }
        return found
    }

fun Module.findVcFilesAndDirectories(modulePath: ModulePath): List<PsiFileSystemItem> {
    val library = TypeCheckingService.getInstance(project).libraryManager.getRegisteredLibrary(name)
    return (library as? VcRawLibrary)?.getHeaderFile()?.findVcFilesAndDirectories(modulePath) ?: emptyList()
}

val Module.isVcModule: Boolean
    get() {
        val moduleFile = moduleFile ?: return false
        if (moduleFile.parent?.findChild(moduleFile.nameWithoutExtension + FileUtils.LIBRARY_EXTENSION) != null) return true
        return containsVcFile
    }
