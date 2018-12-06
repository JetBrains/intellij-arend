package org.arend.module

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import org.arend.error.ErrorReporter
import org.arend.library.LibraryHeader
import org.arend.library.LibraryManager
import org.arend.library.SourceLibrary
import org.arend.library.error.LibraryError
import org.arend.module.util.*
import org.arend.naming.reference.LocatedReferable
import org.arend.source.BinarySource
import org.arend.source.FileBinarySource
import org.arend.source.GZIPStreamBinarySource
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.TypecheckerState
import org.jetbrains.yaml.psi.YAMLFile


class ArendRawLibrary(private val name: String, private val project: Project, headerFile: YAMLFile?, typecheckerState: TypecheckerState): SourceLibrary(typecheckerState) {
    constructor(module: Module, typecheckerState: TypecheckerState):
        this(module.name, module.project, module.libraryConfig, typecheckerState)

    private var headerFilePtr: SmartPsiElementPointer<YAMLFile>? =
        headerFile?.let { SmartPointerManager.getInstance(project).createSmartPsiElementPointer(it) }

    val headerFile: YAMLFile?
        get() = headerFilePtr?.element

    override fun getName() = name

    override fun getModuleGroup(modulePath: ModulePath) = headerFile?.findArendFile(modulePath)

    override fun loadHeader(errorReporter: ErrorReporter): LibraryHeader? {
        val config = headerFile
        if (config == null) {
            errorReporter.report(LibraryError.notFound(name))
            return null
        }
        return LibraryHeader(config.libModules, config.dependencies)
    }

    override fun load(libraryManager: LibraryManager?): Boolean {
        if (!super.load(libraryManager)) {
            setLoaded()
        }
        return true
    }

    override fun getLoadedModules() = headerFile?.libModules ?: emptyList()

    override fun getDependencies() = headerFile?.dependencies ?: emptyList()

    override fun getRawSource(modulePath: ModulePath) =
        headerFile?.findArendFile(modulePath)?.let { ArendRawSource(it) } ?: ArendFakeRawSource(modulePath)

    override fun getBinarySource(modulePath: ModulePath): BinarySource? =
        headerFile?.outputPath?.let { GZIPStreamBinarySource(FileBinarySource(it, modulePath)) }

    override fun containsModule(modulePath: ModulePath) = headerFile?.containsModule(modulePath) == true

    override fun needsTypechecking() = true

    override fun unloadDefinition(referable: LocatedReferable) {
        TypeCheckingService.getInstance(project).updateDefinition(referable)
    }

    override fun getReferableConverter() = TypeCheckingService.getInstance(project).newReferableConverter(true)

    override fun getDependencyListener() = TypeCheckingService.getInstance(project).dependencyListener
}
