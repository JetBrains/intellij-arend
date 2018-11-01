package org.arend.module

import com.intellij.openapi.module.Module
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import org.arend.error.ErrorReporter
import org.arend.library.LibraryHeader
import org.arend.library.LibraryManager
import org.arend.library.SourceLibrary
import org.arend.module.util.*
import org.arend.naming.reference.LocatedReferable
import org.arend.source.BinarySource
import org.arend.source.FileBinarySource
import org.arend.source.GZIPStreamBinarySource
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.TypecheckerState
import org.jetbrains.yaml.psi.YAMLFile


class ArendRawLibrary(private val module: Module, typecheckerState: TypecheckerState): SourceLibrary(typecheckerState) {
    private var headerFilePtr: SmartPsiElementPointer<YAMLFile>? = null

    val headerFile: YAMLFile?
        get() = headerFilePtr?.element

    override fun getName() = module.name

    override fun getModuleGroup(modulePath: ModulePath) = headerFile?.findArendFile(modulePath)

    override fun loadHeader(errorReporter: ErrorReporter): LibraryHeader {
        headerFilePtr = module.libraryConfig?.let { SmartPointerManager.getInstance(module.project).createSmartPsiElementPointer(it) }
        return LibraryHeader(loadedModules, headerFile?.dependencies ?: emptyList())
    }

    override fun load(libraryManager: LibraryManager?): Boolean {
        if (!super.load(libraryManager)) {
            setLoaded()
        }
        return true
    }

    override fun getLoadedModules() = headerFile?.libModules ?: emptyList()

    override fun getRawSource(modulePath: ModulePath) = headerFile?.findArendFile(modulePath)?.let { ArendRawSource(it) } ?: ArendFakeRawSource(modulePath)

    override fun getBinarySource(modulePath: ModulePath): BinarySource? {
        return headerFile?.outputPath?.let { GZIPStreamBinarySource(FileBinarySource(it, modulePath)) }
    }

    override fun containsModule(modulePath: ModulePath) = headerFile?.containsModule(modulePath) == true

    override fun needsTypechecking() = true

    override fun unloadDefinition(referable: LocatedReferable) {
        TypeCheckingService.getInstance(module.project).updateDefinition(referable)
    }

    override fun getReferableConverter() = TypeCheckingService.getInstance(module.project).newReferableConverter(true)

    override fun getDependencyListener() = TypeCheckingService.getInstance(module.project).dependencyListener
}
