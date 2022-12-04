package org.arend.typechecking

import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.arend.library.Library
import org.arend.library.resolver.LibraryResolver
import org.arend.module.ArendModuleType
import org.arend.module.ArendRawLibrary
import org.arend.module.config.ArendModuleConfigService
import org.arend.settings.ArendProjectSettings
import org.arend.util.FileUtils
import org.arend.util.findExternalLibrary
import java.nio.file.Paths


class ArendLibraryResolver(private val project: Project): LibraryResolver {
    override fun resolve(library: Library, name: String): Library? {
        if (!FileUtils.isLibraryName(name)) {
            return null
        }

        val depModule = ModuleManager.getInstance(project).findModuleByName(name)
        if (depModule != null && ArendModuleType.has(depModule)) {
            return ArendModuleConfigService.getInstance(depModule)?.library
        }

        if (library !is ArendRawLibrary) {
            return null
        }

        val libRoot = (library.config as? ArendModuleConfigService)?.librariesRootDef ?: project.service<ArendProjectSettings>().librariesRoot
        return if (libRoot.isNotEmpty()) {
            project.findExternalLibrary(Paths.get(libRoot), name)?.let { ArendRawLibrary(it) }
        } else null
    }
}