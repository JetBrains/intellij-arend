package org.arend.typechecking

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.arend.library.Library
import org.arend.library.resolver.LibraryResolver
import org.arend.module.ArendRawLibrary


class ArendLibraryResolver(private val project: Project): LibraryResolver {
    override fun resolve(name: String): Library? {
        val module = ModuleManager.getInstance(project)?.findModuleByName(name) ?: return null
        return ArendRawLibrary(module, TypeCheckingService.getInstance(project).typecheckerState)
    }
}