package org.arend.module

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import org.arend.ext.error.ErrorReporter
import org.arend.ext.module.ModulePath
import org.arend.library.LibraryHeader
import org.arend.library.LibraryManager
import org.arend.library.SourceLibrary
import org.arend.module.config.ExternalLibraryConfig
import org.arend.module.config.LibraryConfig
import org.arend.naming.reference.*
import org.arend.psi.ArendFile
import org.arend.resolving.ArendReferableConverter
import org.arend.source.BinarySource
import org.arend.source.FileBinarySource
import org.arend.source.GZIPStreamBinarySource
import org.arend.source.PersistableBinarySource
import org.arend.term.group.Group
import org.arend.typechecking.TypeCheckingService
import org.arend.ui.impl.ArendGeneralUI

// TODO[server2]: Delete this.
class ArendRawLibrary(val config: LibraryConfig) : SourceLibrary() {

    override fun isExternal() = config.isExternal

    override fun getName() = config.name

    override fun getVersion() = config.version

    override fun getModuleGroup(modulePath: ModulePath, inTests: Boolean) =
        config.findArendFile(modulePath, inTests)

    override fun getUI() = ArendGeneralUI(config.project)

    override fun loadHeader(errorReporter: ErrorReporter) =
        LibraryHeader(config.findModules(false), config.dependencies, config.version, config.langVersion, config.classLoaderDelegate, config.extensionMainClass)

    override fun unload(): Boolean {
        super.unload()
        config.clearAdditionalModules()
        return isExternal
    }

    override fun getLoadedModules() = config.findModules(false)

    override fun getTestModules() = config.findModules(true)

    override fun getDependencies() = config.dependencies

    override fun getRawSource(modulePath: ModulePath) =
        config.findArendFile(modulePath, false)?.let { ArendRawSource(it) } ?: ArendFakeRawSource(modulePath)

    override fun getTestSource(modulePath: ModulePath) =
        config.findArendFile(modulePath, true)?.let { ArendRawSource(it) } ?: ArendFakeRawSource(modulePath)

    override fun getBinarySource(modulePath: ModulePath): BinarySource? {
        val root = config.root ?: return null
        val binDir = config.binariesDirList ?: return null
        return GZIPStreamBinarySource(IntellijBinarySource(root, binDir, modulePath))
    }

    override fun getPersistableBinarySource(modulePath: ModulePath?): PersistableBinarySource? {
        // We do not persist binary files with VirtualFiles because it takes forever for some reason
        val root = config.root ?: return null
        val binDir = config.binariesDir ?: return null
        val path = root.fileSystem.getNioPath(root) ?: return null
        return GZIPStreamBinarySource(FileBinarySource(path.resolve(binDir), modulePath))
    }

    override fun containsModule(modulePath: ModulePath) = config.containsModule(modulePath)

    override fun resetGroup(group: Group) {
        super.resetGroup(group)
        (group as? ArendFile)?.apply {
            moduleLocation?.let {
                config.project.service<TypeCheckingService>().getTCRefMaps(Referable.RefKind.EXPR).remove(it)
            }
        }
    }

    override fun getReferableConverter() = ArendReferableConverter

    override fun getDependencyListener() = config.project.service<TypeCheckingService>().dependencyListener

    companion object {
        fun getExternalLibrary(libraryManager: LibraryManager, name: String) =
            libraryManager.getRegisteredLibrary { ((it as? ArendRawLibrary)?.config as? ExternalLibraryConfig)?.name == name } as? ArendRawLibrary
    }
}
