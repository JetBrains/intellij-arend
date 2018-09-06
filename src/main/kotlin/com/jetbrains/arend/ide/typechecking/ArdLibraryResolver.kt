package com.jetbrains.arend.ide.typechecking

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.jetbrains.arend.ide.module.ArdRawLibrary
import com.jetbrains.jetpad.vclang.library.Library
import com.jetbrains.jetpad.vclang.library.resolver.LibraryResolver


class ArdLibraryResolver(private val project: Project) : LibraryResolver {
    override fun resolve(name: String): Library? {
        val module = ModuleManager.getInstance(project)?.findModuleByName(name) ?: return null
        return ArdRawLibrary(module, TypeCheckingService.getInstance(project).typecheckerState)
    }
}