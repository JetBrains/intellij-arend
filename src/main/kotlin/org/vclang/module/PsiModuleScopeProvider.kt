package org.vclang.module

import com.intellij.openapi.module.Module
import com.jetbrains.jetpad.vclang.frontend.storage.PreludeStorage
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.module.scopeprovider.ModuleScopeProvider
import com.jetbrains.jetpad.vclang.naming.scope.LexicalScope
import com.jetbrains.jetpad.vclang.naming.scope.Scope
import org.vclang.module.util.findVcFiles


class PsiModuleScopeProvider(private val module: Module): ModuleScopeProvider {
    companion object {
        var preludeScope: Scope? = null // TODO[prelude]: This is an ugly hack
    }

    override fun forModule(modulePath: ModulePath): Scope? {
        if (modulePath == PreludeStorage.PRELUDE_MODULE_PATH) {
            return preludeScope
        }

        val files = module.findVcFiles(modulePath)
        return if (files.size == 1) LexicalScope.opened(files[0]) else null // TODO[library]
    }
}