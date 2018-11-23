package org.arend.typechecking

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.arend.library.Library
import org.arend.library.resolver.LibraryResolver
import org.arend.module.ArendRawLibrary
import org.arend.module.util.findLibHeader


class ArendLibraryResolver(private val project: Project): LibraryResolver {

    override fun resolve(name: String): Library? {
        val module = ModuleManager.getInstance(project)?.findModuleByName(name)

        if (module != null) {
            return ArendRawLibrary(module, TypeCheckingService.getInstance(project).typecheckerState)
        }

        val libHeader = findLibHeader(project, name) ?: return null

        return ArendRawLibrary(libHeader, project, TypeCheckingService.getInstance(project).typecheckerState)
    }
}