package org.arend

import com.intellij.AppTopics
import com.intellij.ProjectTopics
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.startup.StartupActivity
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.Function
import org.arend.library.LibraryDependency
import org.arend.module.ArendRawLibrary
import org.arend.module.util.*
import org.arend.typechecking.TypeCheckingService
import org.jetbrains.yaml.psi.YAMLFile


class ArendStartupActivity : StartupActivity {

    override fun runActivity(project: Project) {
        val service = TypeCheckingService.getInstance(project)
        val addedLibraries = mutableSetOf<String>()

        project.messageBus.connect(project).subscribe(ProjectTopics.MODULES, object : ModuleListener {
            override fun moduleAdded(project: Project, module: Module) {
                if (module.isArendModule) {
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

            override fun modulesRenamed(project: Project, modules: List<Module>, oldNameProvider: Function<Module, String>) {
                for (module in modules) {
                    val oldName = oldNameProvider.`fun`(module) ?: continue
                    service.libraryManager.renameLibrary(oldName, module.name)
                }
            }
        })

        ProjectManager.getInstance().addProjectManagerListener(project, object : ProjectManagerListener {
            override fun projectClosed(project: Project) {
                service.libraryManager.unload()
            }
        })

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

        })

        for (module in project.arendModules) {
            addModule(service, module, addedLibraries)

            module.messageBus.connect().subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
                override fun rootsChanged(event: ModuleRootEvent) {
                    val orderEntries = ModuleRootManager.getInstance(module).orderEntries
                    val libEntriesNames = orderEntries.filter { it is LibraryOrderEntry }.map { (it as LibraryOrderEntry).libraryName }.toMutableSet()
                    libEntriesNames.addAll(orderEntries.filter { it is ModuleOrderEntry }.map { (it as ModuleOrderEntry).moduleName }.toMutableSet())
                    if (!rootsChangedExternally) return
                    ApplicationManager.getApplication().invokeLater {
                        if (module.libraryConfig?.dependencies?.toSet()?.equals(libEntriesNames) != true) {
                            module.libraryConfig?.dependencies = libEntriesNames.map { LibraryDependency(it) }.toList()
                        }
                    }
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
            project.arendModules.forEach { mod -> mod.libraryConfig?.dependencies?.let { depNames.addAll(it.map { it.name })}}

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
        }

        private fun addDependency(module: Module, libName: String, addedLibraries: MutableSet<String>, useInvokeLater: Boolean) {
            val table = LibraryTablesRegistrar.getInstance().getLibraryTable(module.project)
            val tableModel = table.modifiableModel
            var library: Library? = table.getLibraryByName(libName)
            val isExternal = !module.project.arendModules.map { it.name }.contains(libName)

            if (library == null && isExternal) {
                library = tableModel.createLibrary(libName)
                val addLibrary = { WriteAction.run<Exception> {
                    val libModel = library.modifiableModel
                    val libHeader = findLibHeader(module.project, libName)?.let { libHeaderByPath(it, module.project) }
                    val srcDir = libHeader?.sourcesDirPath
                    if (srcDir != null) libModel.addRoot(srcDir.toString(), OrderRootType.SOURCES)
                    val outDir = libHeader?.outputPath
                    if (outDir != null) libModel.addRoot(outDir.toString(), OrderRootType.CLASSES)
                    libModel.commit()
                    tableModel.commit()
                } }

                if (!addedLibraries.contains(libName) && findLibHeader(module.project, libName) != null) {
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
