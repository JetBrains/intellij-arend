package org.vclang.module.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.util.FileUtils
import org.vclang.psi.VcFile

val Module.sourceRoots: List<PsiDirectory>
    get() = ModuleRootManager.getInstance(this).sourceRoots.mapNotNull { PsiManager.getInstance(project).findDirectory(it) }

val Module.contentRoots: List<PsiDirectory>
    get() = ModuleRootManager.getInstance(this).contentRoots.mapNotNull { PsiManager.getInstance(project).findDirectory(it) }

val Module.psiRoots: List<PsiDirectory>
    get() {
        val srcRoots = sourceRoots
        return if (!srcRoots.isEmpty()) srcRoots else contentRoots
    }

val Module.roots: Array<VirtualFile>
    get() = ModuleRootManager.getInstance(this).sourceRoots.let { if (it.isEmpty()) ModuleRootManager.getInstance(this).contentRoots else it }

val Module.vcFiles: List<VcFile>
    get() {
        val result = ArrayList<VcFile>()
        val psiManager = PsiManager.getInstance(project)
        for (root in roots) {
            VfsUtilCore.iterateChildrenRecursively(root, null, { file ->
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

fun Module.findVcFiles(modulePath: ModulePath): List<VcFile> {
    var dirs = psiRoots
    val path = modulePath.toList()
    for ((i, name) in path.withIndex()) {
        if (i < path.size - 1) {
            dirs = dirs.mapNotNull { it.findSubdirectory(name) }
            if (dirs.isEmpty()) return emptyList()
        } else {
            return dirs.mapNotNull { it.findFile(name + FileUtils.EXTENSION) as? VcFile }
        }
    }
    return emptyList()
}

val Module.isVcModule: Boolean
    get() {
        val moduleFile = moduleFile ?: return false
        if (moduleFile.parent?.findChild(moduleFile.nameWithoutExtension + FileUtils.LIBRARY_EXTENSION) != null) return true
        return containsVcFile
    }
