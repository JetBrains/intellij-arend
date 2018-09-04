package org.vclang.module

import com.intellij.openapi.module.Module
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
import org.vclang.module.util.vclFile
import org.vclang.typechecking.TypeCheckingService
import org.vclang.vclpsi.VclFile


class VcRawLibrary(private val module: Module, typecheckerState: TypecheckerState): SourceLibrary(typecheckerState) {
    private var headerFilePtr: SmartPsiElementPointer<VclFile>? = null

    val headerFile: VclFile?
        get() = headerFilePtr?.element

    override fun getName() = module.name

    override fun getModuleGroup(modulePath: ModulePath) = headerFilePtr?.element?.findVcFile(modulePath)

    override fun loadHeader(errorReporter: ErrorReporter): LibraryHeader {
        // TODO: Do not create a header file here
        /*
        if (headerVirtualFile == null) {
            val appendString = {acc:String, s:String ->
                if ("$acc $s".length - "$acc $s".lastIndexOf("\n") > MAX_HEADER_LINE_LENGTH) "$acc\n $s" else "$acc $s"
            }

            val templateStr = loadedModules.fold(MODULES.toString() + COLON) { acc, m -> appendString(acc, m.toString())} + "\n\n" + BINARY + COLON + " " + module.defaultBinDir
            File(headerPath.toString()).printWriter().use { out -> out.print(templateStr) }
            headerVirtualFile = VirtualFileManager.getInstance().findFileByUrl(headerUrl)
        }
        */
        //else if (headerVirtualFile.fileType == VclFileType) {
        headerFilePtr = module.vclFile?.let { SmartPointerManager.getInstance(module.project).createSmartPsiElementPointer(it) }

        /* TODO: implement this properly
        val deps = getHeaderFile()?.dependencies
        if (deps != null) {
            for (dep in deps) {
                WriteAction.run<Exception> {
                    val table = LibraryTablesRegistrar.getInstance().getLibraryTable(module.project)
                    val tableModel = table.modifiableModel
                    val library = tableModel.createLibrary(dep.name)

                    val pathUrl = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, Paths.get("").toString())
                    val file = VirtualFileManager.getInstance().findFileByUrl(pathUrl)
                    if (file != null) {
                        val libraryModel = library.modifiableModel
                        libraryModel.addRoot(file, OrderRootType.CLASSES)
                        libraryModel.commit()
                        tableModel.commit()
                        ModuleRootModificationUtil.addDependency(module.project.vcModules.elementAt(0), library)
                    }
                }
            }
        }*/
        //}
        return LibraryHeader(loadedModules, headerFilePtr?.element?.dependencies ?: emptyList())
    }

    override fun load(libraryManager: LibraryManager?): Boolean {
        if (!super.load(libraryManager)) {
            setLoaded()
        }
        return true
    }

    override fun getLoadedModules() = headerFilePtr?.element?.libModules ?: emptyList()

    override fun getRawSource(modulePath: ModulePath) = headerFilePtr?.element?.findVcFile(modulePath)?.let { VcRawSource(it) } ?: VcFakeRawSource(modulePath)

    override fun getBinarySource(modulePath: ModulePath) = headerFilePtr?.element?.binariesPath?.let { GZIPStreamBinarySource(FileBinarySource(it, modulePath)) }

    override fun containsModule(modulePath: ModulePath) = headerFilePtr?.element?.containsModule(modulePath) == true

    override fun needsTypechecking() = true

    override fun unloadDefinition(referable: LocatedReferable) {
        TypeCheckingService.getInstance(module.project).updateDefinition(referable)
    }

    override fun getReferableConverter() = TypeCheckingService.getInstance(module.project).referableConverter

    override fun getDependencyListener() = TypeCheckingService.getInstance(module.project).dependencyListener
}