package org.arend.typechecking

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.arend.findExternalLibrary
import org.arend.library.Library
import org.arend.library.resolver.LibraryResolver
import org.arend.module.ArendModuleType
import org.arend.module.ArendRawLibrary


class ArendLibraryResolver(private val project: Project): LibraryResolver {
    override fun resolve(name: String): Library? {
        val module = ModuleManager.getInstance(project)?.findModuleByName(name)
        if (module != null && ArendModuleType.has(module)) {
            return ArendRawLibrary(module, TypeCheckingService.getInstance(project).typecheckerState)
        }

        val library = project.findExternalLibrary(name) ?: return null
        return ArendRawLibrary(library, TypeCheckingService.getInstance(project).typecheckerState)
    }
}