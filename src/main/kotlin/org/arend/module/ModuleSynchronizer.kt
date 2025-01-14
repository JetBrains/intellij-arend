package org.arend.module

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.arend.module.config.ArendModuleConfigService
import org.arend.module.config.ExternalLibraryConfig
import org.arend.module.orderRoot.ArendConfigOrderRootType
import org.arend.util.*
import java.nio.file.Paths
import kotlin.Pair

class ModuleSynchronizer(private val project: Project) : ModuleRootListener {
    fun install() {
        project.messageBus.connect().subscribe(ModuleRootListener.TOPIC, this)
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
            synchronizeModule(ArendModuleConfigService.getInstance(module) ?: continue, arendModules, projectTable, false)
        }
    }

    companion object {
        fun synchronizeModule(service: ArendModuleConfigService, reload: Boolean) {
            val arendModules = HashMap<String, Module>()
            for (module in service.project.arendModules) {
                arendModules[module.name] = module
            }
            synchronizeModule(service, arendModules, LibraryTablesRegistrar.getInstance().getLibraryTable(service.project), reload)
        }

        private fun synchronizeModule(service: ArendModuleConfigService, arendModules: Map<String, Module>, projectTable: LibraryTable, reload: Boolean) {
            if (!service.synchronize() || service.module.isDisposed) {
                return
            }
            val ideaDependencies = ArrayList<Any>()

            // Locate dependencies and create libraries in the project-level table if necessary
            val actions = ArrayList<() -> Unit>()
            for (dependency in service.dependencies) {
                val depModule = arendModules[dependency.name]
                if (depModule == service.module) {
                    continue
                }

                if (depModule == null) {
                    val library = runReadAction {
                        val librariesRoot = service.librariesRootDef?.let { VfsUtil.findFile(Paths.get(it), true) }
                        val pair = findLibraryName(projectTable, dependency.name, librariesRoot)
                        var library = pair.first
                        if (library == null) {
                            val externalLibrary = if (librariesRoot == null) null else service.project.findExternalLibrary(librariesRoot, dependency.name)
                            if (librariesRoot != null && externalLibrary != null) {
                                val tableModel = projectTable.modifiableModel
                                library = tableModel.createLibrary(pair.second)

                                val libModel = library.modifiableModel
                                setupFromConfig(libModel, externalLibrary)
                                actions.add {
                                    libModel.commit()
                                    tableModel.commit()
                                }
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

                if (actions.isNotEmpty()) runWriteAction {
                    for (action in actions) {
                        action()
                    }
                }

                val rootModel = runReadAction { ModuleRootManager.getInstance(service.module).modifiableModel }
                try {
                    for (entry in rootModel.orderEntries) {
                        val ideaDependency = (entry as? LibraryOrderEntry)?.library
                            ?: (entry as? ModuleOrderEntry)?.module
                        if (ideaDependency == null && (entry is LibraryOrderEntry || entry is ModuleOrderEntry) || ideaDependency != null && !ideaDependencies.remove(ideaDependency)) {
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

                    runWriteAction {
                        if (!rootModel.isDisposed) {
                            rootModel.commit()
                        }
                    }
                } finally {
                    if (!rootModel.isDisposed) {
                        rootModel.dispose()
                    }
                }

                if (reload) {
                    service.project.service<ReloadLibrariesService>().reload(false)
                }
            }
        }

        private fun findLibraryName(projectTable: LibraryTable, startName: String, librariesRoot: VirtualFile?): Pair<Library?, String> {
            var index = 0
            while (true) {
                val name = if (index == 0) startName else startName + "_" + index
                val library = projectTable.getLibraryByName(name) ?: return Pair(null, name)
                if (librariesRoot != null && (library as? LibraryEx)?.kind is ArendLibraryKind && (library as? LibraryEx)?.isDisposed == false) {
                    val configFile = library.getFiles(ArendConfigOrderRootType.INSTANCE).firstOrNull()
                    if (configFile != null && configFile.libraryName == startName && configFile.libraryRootParent == librariesRoot) {
                        return Pair(library, name)
                    }
                }
                index++
            }
        }

        fun setupFromConfig(libModel: Library.ModifiableModel, config: ExternalLibraryConfig) {
            if (libModel is LibraryEx.ModifiableModelEx) {
                libModel.kind = ArendLibraryKind
            }
            config.root?.findChild(FileUtils.LIBRARY_CONFIG_FILE)?.let {
                libModel.addRoot(it, ArendConfigOrderRootType.INSTANCE)
            }
            if (config.sourcesDir.isNotEmpty()) {
                config.sourcesDirFile?.let { libModel.addRoot(it, OrderRootType.SOURCES) }
            }
        }
    }
}