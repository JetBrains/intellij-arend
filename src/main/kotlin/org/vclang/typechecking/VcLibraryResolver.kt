package org.vclang.typechecking

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.jetbrains.jetpad.vclang.library.Library
import com.jetbrains.jetpad.vclang.library.resolver.LibraryResolver
import org.vclang.module.VcRawLibrary


class VcLibraryResolver(private val project: Project): LibraryResolver {
    override fun resolve(name: String): Library? {
        val module = ModuleManager.getInstance(project)?.findModuleByName(name) ?: return null
        return VcRawLibrary(module)
    }
}