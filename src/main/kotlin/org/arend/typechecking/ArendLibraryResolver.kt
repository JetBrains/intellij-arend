package org.arend.typechecking

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.arend.library.Library
import org.arend.library.resolver.LibraryResolver
import org.arend.module.ArendModuleType
import org.arend.module.ArendRawLibrary
import org.arend.module.config.ArendModuleConfigService
import org.arend.util.FileUtils
import org.arend.util.findExternalLibrary
import java.nio.file.Paths


class ArendLibraryResolver(private val project: Project): LibraryResolver {
    override fun resolve(library: Library, name: String): Library? {
        if (!FileUtils.isLibraryName(name)) {
            return null
        }

        val depModule = ModuleManager.getInstance(project)?.findModuleByName(name)
        if (depModule != null && ArendModuleType.has(depModule)) {
            return ArendModuleConfigService.getInstance(depModule)?.library
        }

        if (library !is ArendRawLibrary) {
            return null
        }

        val config = library.config
        return if (config is ArendModuleConfigService) {
            val root = config.librariesRootDef?.let { Paths.get(it) } ?: return null
            project.findExternalLibrary(root, name)?.let { ArendRawLibrary(it) }
        } else {
            val root = library.config.localFSRoot ?: return null
            project.findExternalLibrary(root, name)?.let { ArendRawLibrary(it) }
        }
    }
}