package org.arend.module

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
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
import java.nio.file.Path
import java.nio.file.Paths


class ArendRawLibrary(private val pathToHeader: Path?, private val project: Project, typecheckerState: TypecheckerState): SourceLibrary(typecheckerState) {
    private var headerFilePtr: SmartPsiElementPointer<YAMLFile>? = null

    val headerFile: YAMLFile?
        get() = headerFilePtr?.element

    constructor(module: Module, typecheckerState: TypecheckerState): this(module.libraryConfig?.virtualFile?.path?.let { Paths.get(it) }, module.project, typecheckerState)

    override fun getName() = headerFile?.libName ?: "noname"

    override fun getModuleGroup(modulePath: ModulePath) = headerFile?.findArendFile(modulePath)

    override fun loadHeader(errorReporter: ErrorReporter): LibraryHeader {
        val libHeader = VirtualFileManager.getInstance().getFileSystem(LocalFileSystem.PROTOCOL).findFileByPath(pathToHeader.toString()) as? YAMLFile
        headerFilePtr = libHeader?.let { SmartPointerManager.getInstance(project).createSmartPsiElementPointer(it) }
        return LibraryHeader(loadedModules, headerFilePtr?.element?.dependencies ?: emptyList())
    }

    override fun load(libraryManager: LibraryManager?): Boolean {
        if (!super.load(libraryManager)) {
            setLoaded()
        }
        return true
    }

    override fun getLoadedModules() = headerFilePtr?.element?.libModules ?: emptyList()

    override fun getRawSource(modulePath: ModulePath) = headerFilePtr?.element?.findArendFile(modulePath)?.let { ArendRawSource(it) } ?: ArendFakeRawSource(modulePath)

    override fun getBinarySource(modulePath: ModulePath): BinarySource? {
        return headerFilePtr?.element?.outputPath?.let { GZIPStreamBinarySource(FileBinarySource(it, modulePath)) }
    }

    override fun containsModule(modulePath: ModulePath) = headerFilePtr?.element?.containsModule(modulePath) == true

    override fun needsTypechecking() = true

    override fun unloadDefinition(referable: LocatedReferable) {
        TypeCheckingService.getInstance(project).updateDefinition(referable)
    }

    override fun getReferableConverter() = TypeCheckingService.getInstance(project).newReferableConverter(true)

    override fun getDependencyListener() = TypeCheckingService.getInstance(project).dependencyListener
}
