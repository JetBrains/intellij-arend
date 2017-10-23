package org.vclang.module

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.naming.scope.ModuleScopeProvider
import com.jetbrains.jetpad.vclang.naming.scope.Scope
import org.vclang.psi.VcFile


class PsiModuleScopeProvider(private val project: Project, private val module: Module): ModuleScopeProvider {
    override fun forModule(modulePath: ModulePath): Scope? {
        var dirs: List<VirtualFile> = ModuleRootManager.getInstance(module).sourceRoots.toList()
        for (name in modulePath.toList()) {
            dirs = dirs.mapNotNull { it.findChild(name) }
            if (dirs.isEmpty()) return null
        }
        return if (dirs.size == 1) (PsiManager.getInstance(project).findFile(dirs[0]) as? VcFile)?.scope /* TODO[abstract]: Replace with the "only exported scope" */ else null
    }
}