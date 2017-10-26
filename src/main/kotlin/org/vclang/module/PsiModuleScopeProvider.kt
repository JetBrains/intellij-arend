package org.vclang.module

import com.intellij.openapi.module.Module
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.naming.scope.EmptyScope
import com.jetbrains.jetpad.vclang.naming.scope.ModuleScopeProvider
import com.jetbrains.jetpad.vclang.naming.scope.PartialLexicalScope
import com.jetbrains.jetpad.vclang.naming.scope.Scope
import org.vclang.module.util.findVcFiles


class PsiModuleScopeProvider(private val module: Module): ModuleScopeProvider {
    companion object {
        var preludeScope: Scope? = null // TODO[prelude]: This is an ugly hack
    }

    override fun forModule(modulePath: ModulePath): Scope? {
        if (modulePath.isSingleton && modulePath.name == "Prelude") {
            return preludeScope
        }

        val files = module.findVcFiles(modulePath)
        return if (files.size == 1) PartialLexicalScope(EmptyScope.INSTANCE, files[0], true) else null
    }
}