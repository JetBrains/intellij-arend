package org.vclang.module

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.FileUtil
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.library.LibraryHeader
import com.jetbrains.jetpad.vclang.library.SourceLibrary
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.naming.reference.LocatedReferable
import com.jetbrains.jetpad.vclang.source.FileBinarySource
import com.jetbrains.jetpad.vclang.source.GZIPStreamBinarySource
import com.jetbrains.jetpad.vclang.typechecking.TypecheckerState
import org.vclang.module.util.findVcFiles
import org.vclang.module.util.vcFiles
import org.vclang.typechecking.TypeCheckingService
import java.nio.file.Path
import java.nio.file.Paths


class VcRawLibrary(private val module: Module, typecheckerState: TypecheckerState): SourceLibrary(typecheckerState) {
    private var baseBinaryPath: Path? = null

    override fun getName() = module.name

    override fun getModuleGroup(modulePath: ModulePath) = module.findVcFiles(modulePath).firstOrNull()

    override fun getBinarySource(modulePath: ModulePath) = if (baseBinaryPath == null) null else GZIPStreamBinarySource(FileBinarySource(baseBinaryPath, modulePath))

    override fun loadHeader(errorReporter: ErrorReporter): LibraryHeader {
        baseBinaryPath = Paths.get(FileUtil.toSystemDependentName(module.moduleFilePath)).resolveSibling(".output")
        return LibraryHeader(loadedModules, emptyList())
    }

    override fun getLoadedModules() = module.vcFiles.map { it.modulePath }

    override fun getRawSource(modulePath: ModulePath) = module.findVcFiles(modulePath).firstOrNull()?.let { VcRawSource(it) }

    override fun needsTypechecking(): Boolean = true

    override fun unloadDefinition(referable: LocatedReferable) {
        TypeCheckingService.getInstance(module.project).updateDefinition(referable)
    }

    override fun getReferableConverter() = TypeCheckingService.getInstance(module.project).referableConverter
}