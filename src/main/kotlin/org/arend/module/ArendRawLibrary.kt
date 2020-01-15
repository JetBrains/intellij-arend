package org.arend.module

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import org.arend.error.ErrorReporter
import org.arend.ext.module.ModulePath
import org.arend.library.LibraryHeader
import org.arend.library.LibraryManager
import org.arend.library.SourceLibrary
import org.arend.module.config.ArendModuleConfigService
import org.arend.module.config.ExternalLibraryConfig
import org.arend.module.config.LibraryConfig
import org.arend.naming.reference.LocatedReferable
import org.arend.psi.ArendDefinition
import org.arend.source.BinarySource
import org.arend.source.FileBinarySource
import org.arend.source.GZIPStreamBinarySource
import org.arend.typechecking.TypeCheckingService

class ArendRawLibrary(val config: LibraryConfig)
    : SourceLibrary(config.project.service<TypeCheckingService>().typecheckerState) {

    constructor(module: Module): this(ArendModuleConfigService.getConfig(module))

    override fun isExternal() = config is ExternalLibraryConfig

    override fun getName() = config.name

    override fun getModuleGroup(modulePath: ModulePath) = config.findArendFile(modulePath)

    override fun mustBeLoaded() = !isExternal

    override fun loadHeader(errorReporter: ErrorReporter) =
        LibraryHeader(config.findModules(), config.dependencies, config.langVersion, config.extensionsPath, config.extensionMainClass)

    override fun unload() = super.unload() && isExternal

    override fun getLoadedModules() = config.findModules()

    override fun getDependencies() = config.dependencies

    override fun getRawSource(modulePath: ModulePath) =
        config.findArendFile(modulePath)?.let { ArendRawSource(it) } ?: ArendFakeRawSource(modulePath)

    override fun getBinarySource(modulePath: ModulePath): BinarySource? =
        config.binariesPath?.let { GZIPStreamBinarySource(FileBinarySource(it, modulePath)) }

    override fun containsModule(modulePath: ModulePath) = config.containsModule(modulePath)

    override fun resetDefinition(referable: LocatedReferable) {
        if (referable !is ArendDefinition) {
            return
        }
        runReadAction {
            if (!config.project.isDisposedOrDisposeInProgress) {
                config.project.service<TypeCheckingService>().updateDefinition(referable, null, TypeCheckingService.LastModifiedMode.DO_NOT_TOUCH)
            }
        }
    }

    override fun getReferableConverter() = config.project.service<TypeCheckingService>().newReferableConverter(true)

    override fun getDependencyListener() = config.project.service<TypeCheckingService>().dependencyListener

    companion object {
        fun getLibraryFor(libraryManager: LibraryManager, module: Module) =
            libraryManager.getRegisteredLibrary { ((it as? ArendRawLibrary)?.config as? ArendModuleConfigService)?.module == module } as? ArendRawLibrary

        fun getExternalLibrary(libraryManager: LibraryManager, name: String) =
            libraryManager.getRegisteredLibrary { ((it as? ArendRawLibrary)?.config as? ExternalLibraryConfig)?.name == name } as? ArendRawLibrary
    }
}
