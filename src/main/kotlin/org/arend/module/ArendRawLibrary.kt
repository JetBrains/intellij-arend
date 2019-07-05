package org.arend.module

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.vfs.*
import org.arend.ArendStartupActivity
import org.arend.error.ErrorReporter
import org.arend.library.LibraryDependency
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
import org.arend.util.FileUtils

class ArendRawLibrary(val config: LibraryConfig, typecheckerState: TypecheckerState): SourceLibrary(typecheckerState) {
    constructor(module: Module, typecheckerState: TypecheckerState):
        this(ArendModuleConfigService.getConfig(module), typecheckerState)

    override fun getName() = config.name

    override fun getModuleGroup(modulePath: ModulePath) = config.findArendFile(modulePath)

    override fun loadHeader(errorReporter: ErrorReporter): LibraryHeader? {
        return LibraryHeader(config.findModules(), config.dependencies)
    }

    override fun load(libraryManager: LibraryManager?): Boolean {
        if (!super.load(libraryManager)) {
            setLoaded()
        }
        VirtualFileManager.getInstance().addVirtualFileListener(object: VirtualFileListener {
            var dependencies: List<LibraryDependency> = emptyList()

            override fun contentsChanged(event: VirtualFileEvent) {
                if (event.fileName != FileUtils.LIBRARY_CONFIG_FILE) {
                    return
                }
                val module = ModuleUtil.findModuleForFile(event.file, config.project) ?: return
                if (module.name != name) {
                    return
                }
                val newDependencies = config.dependencies
                if (newDependencies != dependencies) {
                    for (dep in newDependencies) {
                        if (!dependencies.contains(dep)) {
                            var library = libraryManager?.getRegisteredLibrary(dep.name)
                            if (library == null) {
                                library = libraryManager?.loadLibrary(dep.name)
                            }
                            if (library != null) {
                                libraryManager?.registerDependency(this@ArendRawLibrary, library)
                            }
                        }
                    }
                }
            }
        }, config.project)
        return true
    }

    override fun getLoadedModules() = config.findModules()

    override fun getDependencies() = config.dependencies

    override fun getRawSource(modulePath: ModulePath) =
        config.findArendFile(modulePath)?.let { ArendRawSource(it) } ?: ArendFakeRawSource(modulePath)

    override fun getBinarySource(modulePath: ModulePath): BinarySource? =
        config.binariesPath?.let { GZIPStreamBinarySource(FileBinarySource(it, modulePath)) }

    override fun containsModule(modulePath: ModulePath) = config.containsModule(modulePath)

    override fun needsTypechecking() = true

    override fun unloadDefinition(referable: LocatedReferable) {
        runReadAction { TypeCheckingService.getInstance(config.project).updateDefinition(referable) }
    }

    override fun getReferableConverter() = TypeCheckingService.getInstance(config.project).newReferableConverter(true)

    override fun getDependencyListener() = TypeCheckingService.getInstance(config.project).dependencyListener
}
