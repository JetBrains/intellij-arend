package org.vclang.module

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.library.LibraryHeader
import com.jetbrains.jetpad.vclang.library.LibraryManager
import com.jetbrains.jetpad.vclang.library.SourceLibrary
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.naming.reference.LocatedReferable
import com.jetbrains.jetpad.vclang.source.FileBinarySource
import com.jetbrains.jetpad.vclang.source.GZIPStreamBinarySource
import com.jetbrains.jetpad.vclang.typechecking.TypecheckerState
import com.jetbrains.jetpad.vclang.util.FileUtils
import org.vclang.VclFileType
import org.vclang.vclpsi.*
import org.vclang.typechecking.TypeCheckingService
import java.nio.file.Paths
import org.vclang.vclpsi.VclElementTypes.*
import org.vclang.module.util.getVcFiles
import java.io.File


class VcRawLibrary(private val module: Module, typecheckerState: TypecheckerState): SourceLibrary(typecheckerState) {
    private var headerFile: SmartPsiElementPointer<VclFile>? = null

    companion object {
        const val MAX_HEADER_LINE_LENGTH = 100
    }

    fun getHeaderFile(): VclFile? = headerFile?.element

    override fun getName() = module.name

    override fun getModuleGroup(modulePath: ModulePath) =
            headerFile?.element?.findVcFiles(modulePath)?.firstOrNull { loadedModules.contains(it.modulePath) }

    override fun getBinarySource(modulePath: ModulePath) =
            headerFile?.element?.binariesDirPath.let {GZIPStreamBinarySource(FileBinarySource(it, modulePath))}

    override fun loadHeader(errorReporter: ErrorReporter): LibraryHeader {
        val headerPath = Paths.get(FileUtil.toSystemDependentName(module.moduleFilePath)).resolveSibling(name + FileUtils.LIBRARY_EXTENSION)
        val headerUrl = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL,
                headerPath.toString())
        val headerVirtFile = VirtualFileManager.getInstance().findFileByUrl(headerUrl)
        if (headerVirtFile == null) {
            val appendString = {acc:String, s:String ->
                if ("$acc $s".length - "$acc $s".lastIndexOf("\n") > MAX_HEADER_LINE_LENGTH) "$acc\n $s" else "$acc $s"
            }

            val templateStr = loadedModules.fold(MODULES.toString() + COLON,
                    {acc, m -> appendString(acc, m.toString())}) + "\n" +
                    "\n" + BINARY + COLON + " " + VclFile.getDefaultBinPath(module)?.toString()
            File(headerPath.toString()).printWriter().use { out -> out.print(templateStr) }
        } else if (headerVirtFile.fileType == VclFileType) {
            headerFile = (PsiManager.getInstance(module.project).findFile(headerVirtFile) as VclFile).let{
                SmartPointerManager.getInstance(module.project).createSmartPsiElementPointer(it)
            }
        }
        return LibraryHeader(loadedModules, headerFile?.element?.dependencies ?: emptyList())
    }

    override fun load(libraryManager: LibraryManager?): Boolean {
        if (!super.load(libraryManager)) {
            setLoaded()
        }
        return true
    }

    override fun getLoadedModules() = headerFile?.element?.libModules ?: module.getVcFiles(module.moduleFile?.parent).map { it.modulePath }

    override fun getRawSource(modulePath: ModulePath) =  headerFile?.element?.findVcFiles(modulePath)?.firstOrNull()?.let { VcRawSource(it) }
    // module.findVcFiles(modulePath).firstOrNull()?.let { VcRawSource(it) }

    override fun needsTypechecking(): Boolean = true

    override fun unloadDefinition(referable: LocatedReferable) {
        TypeCheckingService.getInstance(module.project).updateDefinition(referable)
    }

    override fun getReferableConverter() = TypeCheckingService.getInstance(module.project).referableConverter

    override fun getDependencyListener() = TypeCheckingService.getInstance(module.project).dependencyListener
}