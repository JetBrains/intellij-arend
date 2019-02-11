package org.arend.module

import com.intellij.openapi.module.Module
import org.arend.error.ErrorReporter
import org.arend.library.LibraryHeader
import org.arend.library.LibraryManager
import org.arend.library.SourceLibrary
import org.arend.module.config.ArendModuleConfigService
import org.arend.module.config.LibraryConfig
import org.arend.naming.reference.LocatedReferable
import org.arend.source.BinarySource
import org.arend.source.FileBinarySource
import org.arend.source.GZIPStreamBinarySource
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.TypecheckerState


class ArendRawLibrary(val config: LibraryConfig, typecheckerState: TypecheckerState): SourceLibrary(typecheckerState) {
    constructor(module: Module, typecheckerState: TypecheckerState):
        this(ArendModuleConfigService.getInstance(module), typecheckerState)

    override fun getName() = config.name

    override fun getModuleGroup(modulePath: ModulePath) = config.findArendFile(modulePath)

    override fun loadHeader(errorReporter: ErrorReporter): LibraryHeader? {
        return LibraryHeader(config.findModules(), config.dependencies)
    }

    override fun load(libraryManager: LibraryManager?): Boolean {
        if (!super.load(libraryManager)) {
            setLoaded()
        }
        return true
    }

    override fun getLoadedModules() = config.findModules()

    override fun getDependencies() = config.dependencies

    override fun getRawSource(modulePath: ModulePath) =
        config.findArendFile(modulePath)?.let { ArendRawSource(it) } ?: ArendFakeRawSource(modulePath)

    override fun getBinarySource(modulePath: ModulePath): BinarySource? =
        config.outputPath?.let { GZIPStreamBinarySource(FileBinarySource(it, modulePath)) }

    override fun containsModule(modulePath: ModulePath) = config.containsModule(modulePath)

    override fun needsTypechecking() = true

    override fun unloadDefinition(referable: LocatedReferable) {
        TypeCheckingService.getInstance(config.project).updateDefinition(referable)
    }

    override fun getReferableConverter() = TypeCheckingService.getInstance(config.project).newReferableConverter(true)

    override fun getDependencyListener() = TypeCheckingService.getInstance(config.project).dependencyListener
}
