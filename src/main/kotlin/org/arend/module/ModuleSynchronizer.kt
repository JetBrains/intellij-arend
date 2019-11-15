package org.arend.module

import com.intellij.ProjectTopics
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import org.arend.module.config.ArendModuleConfigService
import org.arend.module.orderRoot.ArendConfigOrderRootType
import org.arend.util.FileUtils
import org.arend.util.arendModules
import org.arend.util.findExternalLibrary
import java.nio.file.Paths

class ModuleSynchronizer(private val project: Project) : ModuleRootListener {
    fun install() {
        project.messageBus.connect().subscribe(ProjectTopics.PROJECT_ROOTS, this)
        synchronizeModules()
    }

    override fun rootsChanged(event: ModuleRootEvent) {
        synchronizeModules()
    }

    private fun synchronizeModules() {
        val arendModules = HashMap<String, Module>()
        val moduleList = project.arendModules
        for (module in moduleList) {
            arendModules[module.name] = module
        }

        val projectTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
        for (module in moduleList) {
            synchronizeModule(ArendModuleConfigService.getInstance(module) ?: continue, arendModules, projectTable)
        }
    }

    companion object {
        fun synchronizeModule(service: ArendModuleConfigService) {
            val arendModules = HashMap<String, Module>()
            for (module in service.project.arendModules) {
                arendModules[module.name] = module
            }
            synchronizeModule(service, arendModules, LibraryTablesRegistrar.getInstance().getLibraryTable(service.project))
        }

        private fun synchronizeModule(service: ArendModuleConfigService, arendModules: Map<String, Module>, projectTable: LibraryTable) {
            if (!service.synchronize() || service.module.isDisposed) {
                return
            }
            val ideaDependencies = ArrayList<Any>()

            // Locate dependencies and create libraries in the project-level table if necessary
            for (dependency in service.dependencies) {
                val depModule = arendModules[dependency.name]
                if (depModule == service.module) {
                    continue
                }

                if (depModule == null) {
                    val library = runReadAction {
                        val pair = findLibraryName(projectTable, dependency.name)
                        var library = pair.first
                        if (library == null) {
                            val librariesRoot = service.librariesRootDef
                            val externalLibrary = if (librariesRoot == null) null else service.project.findExternalLibrary(Paths.get(librariesRoot), dependency.name)
                            if (externalLibrary != null) {
                                val tableModel = projectTable.modifiableModel
                                library = tableModel.createLibrary(pair.second)

                                val libModel = library.modifiableModel
                                if (libModel is LibraryEx.ModifiableModelEx) {
                                    libModel.kind = ArendLibraryKind
                                }

                                libModel.addRoot(VfsUtil.pathToUrl(FileUtil.join(librariesRoot, dependency.name, FileUtils.LIBRARY_CONFIG_FILE)), ArendConfigOrderRootType)
                                externalLibrary.sourcesPath?.let {
                                    libModel.addRoot(VfsUtil.pathToUrl(it.toString()), OrderRootType.SOURCES)
                                }
                                ApplicationManager.getApplication()?.invokeAndWait { runWriteAction {
                                    libModel.commit()
                                    tableModel.commit()
                                } }
                            }
                        }
                        library
                    }

                    if (library != null) {
                        ideaDependencies.add(library)
                    }
                } else {
                    ideaDependencies.add(depModule)
                }
            }

            // Update the module-level library table
            ApplicationManager.getApplication()?.invokeLater {
                if (service.module.isDisposed) {
                    return@invokeLater
                }

                val rootModel = runReadAction { ModuleRootManager.getInstance(service.module).modifiableModel }
                try {
                    for (entry in rootModel.orderEntries) {
                        val ideaDependency = (entry as? LibraryOrderEntry)?.library
                            ?: (entry as? ModuleOrderEntry)?.module
                        if (ideaDependency != null && !ideaDependencies.remove(ideaDependency)) {
                            rootModel.removeOrderEntry(entry)
                        }
                    }
                    for (ideaDependency in ideaDependencies) {
                        if (ideaDependency is Library) {
                            rootModel.addLibraryEntry(ideaDependency)
                        }
                        if (ideaDependency is Module) {
                            rootModel.addModuleOrderEntry(ideaDependency)
                        }
                    }

                    runInEdt { runWriteAction {
                        if (!rootModel.isDisposed) {
                            rootModel.commit()
                        }
                    } }
                } finally {
                    if (!rootModel.isDisposed) {
                        rootModel.dispose()
                    }
                }
            }
        }

        private fun findLibraryName(projectTable: LibraryTable, startName: String): Pair<Library?, String> {
            var index = 0
            while (true) {
                val name = if (index == 0) startName else startName + "_" + index
                val library = projectTable.getLibraryByName(name) ?: return Pair(null, name)
                if ((library as? LibraryEx)?.kind is ArendLibraryKind && (library as? LibraryEx)?.isDisposed == false && library.getFiles(ArendConfigOrderRootType).firstOrNull()?.parent?.name == startName) {
                    return Pair(library, name)
                }
                index++
            }
        }
    }
}