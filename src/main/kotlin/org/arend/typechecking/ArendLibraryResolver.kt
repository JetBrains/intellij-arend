package org.arend.typechecking

import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.arend.library.Library
import org.arend.library.resolver.LibraryResolver
import org.arend.module.ArendModuleType
import org.arend.module.ArendRawLibrary
import org.arend.module.config.ArendModuleConfigService
import org.arend.util.FileUtils
import org.arend.util.findExternalLibrary
import org.arend.util.librariesRoot
import java.nio.file.Paths


class ArendLibraryResolver(private val project: Project): LibraryResolver {
    override fun resolve(library: Library, name: String): Library? {
        if (!FileUtils.isLibraryName(name)) {
            return null
        }

        val depModule = ModuleManager.getInstance(project)?.findModuleByName(name)
        if (depModule != null && ArendModuleType.has(depModule)) {
            return ArendRawLibrary.getLibraryFor(project.service<TypeCheckingService>().libraryManager, depModule) ?: ArendRawLibrary(depModule)
        }

        if (library !is ArendRawLibrary) {
            return null
        }

        val module = (library.config as? ArendModuleConfigService)?.module
        val root = if (module == null) {
            library.config.rootPath?.parent
        } else {
            module.librariesRoot?.let { Paths.get(it) }
        } ?: return null

        return project.findExternalLibrary(root, name)?.let { ArendRawLibrary(it) }
    }
}