package org.arend.module

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.libraries.Library
import org.arend.error.ErrorReporter
import org.arend.library.LibraryHeader
import org.arend.library.LibraryManager
import org.arend.library.SourceLibrary
import org.arend.module.config.ArendModuleConfigService
import org.arend.module.config.ExternalLibraryConfig
import org.arend.module.config.LibraryConfig
import org.arend.naming.reference.LocatedReferable
import org.arend.source.BinarySource
import org.arend.source.FileBinarySource
import org.arend.source.GZIPStreamBinarySource
import org.arend.typechecking.TypeCheckingService

class ArendRawLibrary(val config: LibraryConfig, val isExternal: Boolean): SourceLibrary(TypeCheckingService.getInstance(config.project).typecheckerState) {
    constructor(module: Module): this(ArendModuleConfigService.getConfig(module), false)

    override fun getName() = config.name

    override fun getModuleGroup(modulePath: ModulePath) = config.findArendFile(modulePath)

    override fun loadHeader(errorReporter: ErrorReporter) =
        LibraryHeader(config.findModules(), config.dependencies)

    override fun load(libraryManager: LibraryManager?): Boolean {
        if (!super.load(libraryManager)) {
            setLoaded()
        }
        return true
    }

    override fun unload() =
        if (isExternal) {
            super.unload()
        } else {
            reset()
            false
        }

    override fun getLoadedModules() = config.findModules()

    override fun getDependencies() = config.dependencies

    override fun getRawSource(modulePath: ModulePath) =
        config.findArendFile(modulePath)?.let { ArendRawSource(it) } ?: ArendFakeRawSource(modulePath)

    override fun getBinarySource(modulePath: ModulePath): BinarySource? =
        config.binariesPath?.let { GZIPStreamBinarySource(FileBinarySource(it, modulePath)) }

    override fun containsModule(modulePath: ModulePath) = config.containsModule(modulePath)

    override fun needsTypechecking() = true

    override fun resetDefinition(referable: LocatedReferable) {
        runReadAction { TypeCheckingService.getInstance(config.project).updateDefinition(referable) }
    }

    override fun getReferableConverter() = TypeCheckingService.getInstance(config.project).newReferableConverter(true)

    override fun getDependencyListener() = TypeCheckingService.getInstance(config.project).dependencyListener

    companion object {
        fun getLibraryFor(libraryManager: LibraryManager, module: Module) =
            libraryManager.getRegisteredLibrary { ((it as? ArendRawLibrary)?.config as? ArendModuleConfigService)?.module == module } as? ArendRawLibrary

        fun getExternalLibrary(libraryManager: LibraryManager, name: String) =
            libraryManager.getRegisteredLibrary { ((it as? ArendRawLibrary)?.config as? ExternalLibraryConfig)?.name == name } as? ArendRawLibrary
    }
}
