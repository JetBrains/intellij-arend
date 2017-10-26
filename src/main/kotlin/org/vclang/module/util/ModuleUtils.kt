package org.vclang.module.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.jetbrains.jetpad.vclang.module.ModulePath
import org.vclang.VcFileType
import org.vclang.psi.VcFile

val Module.sourceRoots: List<PsiDirectory>
    get() = ModuleRootManager.getInstance(this).sourceRoots.mapNotNull { PsiManager.getInstance(project).findDirectory(it) }

fun Module.findVcFiles(modulePath: ModulePath): List<VcFile> {
    var dirs = sourceRoots
    val path = modulePath.toList()
    for ((i, name) in path.withIndex()) {
        if (i < path.size - 1) {
            dirs = dirs.mapNotNull { it.findSubdirectory(name) }
            if (dirs.isEmpty()) return emptyList()
        } else {
            return dirs.mapNotNull { it.findFile(name + "." + VcFileType.defaultExtension) as? VcFile }
        }
    }
    return emptyList()
}
