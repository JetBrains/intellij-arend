package org.vclang.module

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.naming.scope.EmptyScope
import com.jetbrains.jetpad.vclang.naming.scope.ModuleScopeProvider
import com.jetbrains.jetpad.vclang.naming.scope.PartialLexicalScope
import com.jetbrains.jetpad.vclang.naming.scope.Scope
import org.vclang.psi.VcFile


class PsiModuleScopeProvider(private val module: Module): ModuleScopeProvider {
    companion object {
        var preludeScope: Scope? = null // TODO[prelude]: This is an ugly hack
    }

    override fun forModule(modulePath: ModulePath): Scope? {
        if (modulePath.isSingleton && modulePath.name == "Prelude") {
            return preludeScope
        }

        var dirs: List<VirtualFile> = ModuleRootManager.getInstance(module).sourceRoots.toList()
        for (name in modulePath.toList()) {
            dirs = dirs.mapNotNull { it.findChild(name) }
            if (dirs.isEmpty()) return null
        }
        return if (dirs.size == 1) (PsiManager.getInstance(module.project).findFile(dirs[0]) as? VcFile)?.let { PartialLexicalScope(EmptyScope.INSTANCE, it, true) } else null
    }
}