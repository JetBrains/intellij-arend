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

    override fun getModuleGroup(modulePath: ModulePath) = headerFilePtr?.element?.findArendFile(modulePath)

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
        //else if (headerVirtualFile.fileType == YAMLFileType) {
        headerFilePtr = module.libraryConfig?.let { SmartPointerManager.getInstance(module.project).createSmartPsiElementPointer(it) }

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
                        ModuleRootModificationUtil.addDependency(module.project.arendModules.elementAt(0), library)
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

    override fun getRawSource(modulePath: ModulePath) = headerFilePtr?.element?.findArendFile(modulePath)?.let { ArendRawSource(it) } ?: ArendFakeRawSource(modulePath)

    override fun getBinarySource(modulePath: ModulePath): BinarySource? {
        return headerFilePtr?.element?.outputPath?.let { GZIPStreamBinarySource(FileBinarySource(it, modulePath)) }
    }

    override fun containsModule(modulePath: ModulePath) = headerFilePtr?.element?.containsModule(modulePath) == true

    override fun needsTypechecking() = true

    override fun unloadDefinition(referable: LocatedReferable) {
        TypeCheckingService.getInstance(module.project).updateDefinition(referable)
    }

    override fun getReferableConverter() = TypeCheckingService.getInstance(module.project).newReferableConverter(true)

    override fun getDependencyListener() = TypeCheckingService.getInstance(module.project).dependencyListener
}
