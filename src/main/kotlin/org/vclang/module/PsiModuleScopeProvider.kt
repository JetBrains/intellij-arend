package org.vclang.module

import com.intellij.openapi.module.Module
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.module.scopeprovider.ModuleScopeProvider
import com.jetbrains.jetpad.vclang.naming.scope.LexicalScope
import com.jetbrains.jetpad.vclang.naming.scope.MergeScope
import com.jetbrains.jetpad.vclang.naming.scope.Scope
import com.jetbrains.jetpad.vclang.prelude.Prelude
import org.vclang.module.util.findVcFiles


class PsiModuleScopeProvider(private val module: Module): ModuleScopeProvider {
    companion object {
        var preludeScope: Scope? = null // TODO[prelude]: This is an ugly hack
    }

    override fun forModule(modulePath: ModulePath): Scope? {
        if (modulePath == Prelude.MODULE_PATH) {
            return preludeScope
        }

        val files = module.findVcFiles(modulePath)
        if (files.isEmpty()) return null
        if (files.size == 1) return LexicalScope.opened(files[0])
        return MergeScope(files.map { LexicalScope.opened(it) })
    }
}