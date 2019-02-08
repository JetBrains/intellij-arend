package org.arend

import com.intellij.ProjectTopics
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.VfsUtil
import org.arend.module.ArendModuleType
import org.arend.module.ArendRawLibrary
import org.arend.typechecking.TypeCheckingService


class ArendStartupActivity : StartupActivity {

    override fun runActivity(project: Project) {
        val service = TypeCheckingService.getInstance(project)
        val addedLibraries = mutableSetOf<String>()

        project.messageBus.connect(project).subscribe(ProjectTopics.MODULES, object : ModuleListener {
            override fun moduleAdded(project: Project, module: Module) {
                if (ArendModuleType.has(module)) {
                    addModule(service, module, addedLibraries)
                }
            }

            override fun beforeModuleRemoved(project: Project, module: Module) {
                service.libraryManager.getRegisteredLibrary(module.name)?.let { service.libraryManager.unloadLibrary(it) }
            }

            override fun moduleRemoved(project: Project, module: Module) {
                syncModuleDependencies(module, mutableSetOf(), true)
                cleanObsoleteProjectLibraries(project)
            }
        })

        ProjectManager.getInstance().addProjectManagerListener(project, object : ProjectManagerListener {
            override fun projectClosed(project: Project) {
                service.libraryManager.unload()
            }
        })

        /* TODO[libraries]
        project.messageBus.connect().subscribe(AppTopics.FILE_DOCUMENT_SYNC, object : FileDocumentManagerListener {
            override fun beforeDocumentSaving(document: Document) {
                val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) as? YAMLFile ?: return

                for (module in project.arendModules) {
                    if (module.name == psiFile.libName) {
                        syncModuleDependencies(module, mutableSetOf(), false)
                    }
                }
                cleanObsoleteProjectLibraries(project)
            }

        }) */

        for (module in project.arendModules) {
            addModule(service, module, addedLibraries)

            module.messageBus.connect().subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
                override fun rootsChanged(event: ModuleRootEvent) {
                    val orderEntries = ModuleRootManager.getInstance(module).orderEntries
                    val libEntriesNames = orderEntries.filter { it is LibraryOrderEntry }.map { (it as LibraryOrderEntry).libraryName }.toMutableSet()
                    libEntriesNames.addAll(orderEntries.filter { it is ModuleOrderEntry }.map { (it as ModuleOrderEntry).moduleName }.toMutableSet())
                    if (!rootsChangedExternally) return
                    /* TODO[libraries]
                    ApplicationManager.getApplication().invokeLater {
                        if (module.libraryConfig?.dependencies?.toSet()?.equals(libEntriesNames) != true) {
                            module.libraryConfig?.dependencies = libEntriesNames.map { LibraryDependency(it) }.toList()
                        }
                    }
                    */
                }
            })
        }
    }

    companion object {
        var rootsChangedExternally = true

        private fun addModule(service: TypeCheckingService, module: Module, addedLibraries: MutableSet<String>) {
            service.initialize()
            service.libraryManager.loadLibrary(ArendRawLibrary(module, service.typecheckerState))
            syncModuleDependencies(module, addedLibraries, true)
        }

        private fun cleanObsoleteProjectLibraries(project: Project) {
            val depNames = mutableSetOf<String>()
            // TODO[libraries]
            // project.arendModules.forEach { mod -> mod.libraryConfig?.dependencies?.let { deps -> depNames.addAll(deps.map { it.name })}}

            val table = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
            for (library in table.libraries) {
                if (!depNames.contains(library.name)) {
                    ApplicationManager.getApplication().invokeLater {
                        WriteAction.run<Exception> {
                            table.removeLibrary(library)
                        }
                    }
                }
            }
        }

        private fun syncModuleDependencies(module: Module, addedLibraries: MutableSet<String>, useInvokeLater: Boolean) {
            return
            /* TODO[libraries]
            val deps = module.libraryConfig?.dependencies ?: return
            val depNames = deps.map { it.name }

            rootsChangedExternally = false
            for (dep in deps) {
                addDependency(module, dep.name, addedLibraries, useInvokeLater)
                addedLibraries.add(dep.name)
            }

            WriteAction.run<Exception> {
                val rootModel = ModuleRootManager.getInstance(module).modifiableModel
                for (entry in rootModel.orderEntries) {
                    if (entry is LibraryOrderEntry && !depNames.contains(entry.libraryName)) {
                        rootModel.removeOrderEntry(entry)
                    }
                    if (entry is ModuleOrderEntry && !depNames.contains(entry.moduleName)) {
                        rootModel.removeOrderEntry(entry)
                    }
                }
                rootModel.commit()
                rootsChangedExternally = true
            }
            */
        }

        private fun addDependency(module: Module, libName: String, addedLibraries: MutableSet<String>, useInvokeLater: Boolean) {
            val table = LibraryTablesRegistrar.getInstance().getLibraryTable(module.project)
            val tableModel = table.modifiableModel
            var library: Library? = table.getLibraryByName(libName)
            val isExternal = !module.project.arendModules.map { it.name }.contains(libName)

            if (library == null && isExternal) {
                library = tableModel.createLibrary(libName)
                val libConfig = module.project.findExternalLibrary(libName)
                if (libConfig != null && !addedLibraries.contains(libName)) {
                    val addLibrary = {
                        WriteAction.run<Exception> {
                            val libModel = library.modifiableModel
                            val srcDir = libConfig.sourcesPath
                            if (srcDir != null) {
                                libModel.addRoot(VfsUtil.pathToUrl(srcDir.toString()), OrderRootType.SOURCES)
                            }
                            val outDir = libConfig.outputPath
                            if (outDir != null) {
                                libModel.addRoot(VfsUtil.pathToUrl(outDir.toString()), OrderRootType.CLASSES)
                            }
                            libModel.commit()
                            tableModel.commit()
                        }
                    }
                    if (useInvokeLater) {
                        ApplicationManager.getApplication().invokeLater { addLibrary() }
                    } else {
                        addLibrary()
                    }
                }
            }

            WriteAction.run<Exception> {
                val orderEntries = ModuleRootManager.getInstance(module).orderEntries
                val rootModel = ModuleRootManager.getInstance(module).modifiableModel
                if (isExternal) {
                    val libEntriesNames = orderEntries.filter { it is LibraryOrderEntry }.map { (it as LibraryOrderEntry).libraryName }
                    if (!libEntriesNames.contains(libName)) {
                        rootModel.addLibraryEntry(library!!)
                    }
                } else {
                    val modEntriesNames = orderEntries.filter { it is ModuleOrderEntry }.map { (it as ModuleOrderEntry).moduleName }
                    val depModule = module.project.arendModules.find { it.name == libName }
                    if (!modEntriesNames.contains(libName)) {
                        depModule?.let { rootModel.addModuleOrderEntry(it) }
                    }
                }
                rootModel.commit()
            }
        }

    }
}
