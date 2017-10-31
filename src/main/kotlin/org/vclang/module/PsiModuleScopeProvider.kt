package org.vclang.module

import com.intellij.openapi.module.Module
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.naming.scope.*
import org.vclang.module.util.findVcFiles


class PsiModuleScopeProvider(private val module: Module): ModuleScopeProvider {
    companion object {
        var preludeScope: Scope? = null // TODO[prelude]: This is an ugly hack
    }

    override fun forModule(modulePath: ModulePath, includeExports: Boolean): Scope? {
        if (modulePath.isSingleton && modulePath.name == "Prelude") {
            return preludeScope
        }

        val files = module.findVcFiles(modulePath)
        return if (files.size == 1) (if (includeExports) LexicalScope.opened(files[0]) else LexicalScope.exported(files[0]) ) else null
    }
}