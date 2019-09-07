package org.arend.module.config

import com.intellij.notification.NotificationType
import com.intellij.openapi.application.*
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleServiceManager
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.arend.library.LibraryDependency
import org.arend.module.ArendLibraryKind
import org.arend.module.ArendModuleType
import org.arend.module.ArendRawLibrary
import org.arend.module.ModulePath
import org.arend.module.orderRoot.ArendConfigOrderRootType
import org.arend.settings.ArendSettings
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.util.*
import org.jetbrains.yaml.psi.YAMLFile
import java.nio.file.Paths
import kotlin.Pair


class ArendModuleConfigService(val module: Module) : LibraryConfig(module.project), ArendModuleConfiguration {
    private val libraryManager = project.service<TypeCheckingService>().libraryManager

    override var librariesRoot = service<ArendSettings>().librariesRoot
        set(value) {
            field = value
            service<ArendSettings>().librariesRoot = value
        }

    override var sourcesDir = ""
    override var withBinaries = false
    override var binariesDirectory = ""
    override var modules: List<ModulePath>? = null
    override var dependencies: List<LibraryDependency> = emptyList()

    override val binariesDir: String?
        get() = flaggedBinariesDir

    val root
        get() = ModuleRootManager.getInstance(module).contentEntries.firstOrNull()?.file

    override val rootDir
        get() = root?.path

    override val name
        get() = module.name

    override val sourcesDirFile: VirtualFile?
        get() {
            val dir = sourcesDir
            if (dir.isEmpty()) {
                return root
            }

            val root = root
            val path = when {
                root != null -> Paths.get(root.path).resolve(dir).toString()
                Paths.get(dir).isAbsolute -> dir
                else -> return null
            }
            return VirtualFileManager.getInstance().getFileSystem(LocalFileSystem.PROTOCOL).findFileByPath(path)
        }

    private val yamlFile
        get() = rootPath?.resolve(FileUtils.LIBRARY_CONFIG_FILE)?.let { project.findPsiFileByPath(it) as? YAMLFile }

    val librariesRootDef: String?
        get() {
            val librariesRoot = librariesRoot
            return if (librariesRoot.isEmpty()) {
                root?.parent?.path?.let { FileUtil.toSystemDependentName(it) }
            } else librariesRoot
        }

    private fun updateDependencies(newDependencies: List<LibraryDependency>, reload: Boolean) {
        val oldDependencies = dependencies
        dependencies = ArrayList(newDependencies)

        if (!reload) {
            return
        }

        val library = ArendRawLibrary.getLibraryFor(libraryManager, module) ?: return
        var reloadLib = false
        for (dependency in oldDependencies) {
            if (!newDependencies.contains(dependency) && libraryManager.getRegisteredLibrary(dependency.name) != null) {
                reloadLib = true
                break
            }
        }

        if (reloadLib) {
            libraryManager.unloadLibrary(library)
            libraryManager.loadLibrary(library)
        } else {
            for (dependency in newDependencies) {
                if (!oldDependencies.contains(dependency)) {
                    var depLibrary = libraryManager.getRegisteredLibrary(dependency.name)
                    if (depLibrary == null) {
                        depLibrary = libraryManager.loadDependency(library, dependency.name)
                    }
                    if (depLibrary != null) {
                        libraryManager.registerDependency(library, depLibrary)
                    }
                }
            }
        }
    }

    fun copyFromYAML(yaml: YAMLFile, update: Boolean) {
        val newDependencies = yaml.dependencies
        if (dependencies != newDependencies) {
            updateDependencies(newDependencies, update)
            updateIdeaDependencies(null)
        }

        modules = yaml.modules
        flaggedBinariesDir = yaml.binariesDir
        sourcesDir = yaml.sourcesDir ?: ""
    }

    fun copyFromYAML() {
        copyFromYAML(yamlFile ?: return, true)
    }

    private fun updateIdeaDependencies(state: ModuleConfigurationState?) {
        val librariesRoot = librariesRootDef ?: return

        val ideaDependencies = ArrayList<Any>()
        val arendModules = HashMap<String,Module>()
        for (depModule in project.arendModules) {
            arendModules[depModule.name] = depModule
        }

        // Locate dependencies and create libraries in the project-level table if necessary
        val projectTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
        for (dependency in dependencies) {
            val depModule = arendModules[dependency.name]
            if (depModule == module) {
                continue
            }

            if (depModule == null) {
                val library = runReadAction {
                    val pair = findLibraryName(projectTable, dependency.name)
                    var library = pair.first
                    if (library == null) {
                        val externalLibrary = project.findExternalLibrary(Paths.get(librariesRoot), dependency.name)
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
                            runInEdt { runWriteAction {
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
        val updater = { rootModel: ModifiableRootModel ->
            for (entry in rootModel.orderEntries) {
                val ideaDependency = (entry as? LibraryOrderEntry)?.library ?: (entry as? ModuleOrderEntry)?.module
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
        }

        if (state != null) {
            updater(state.rootModel)
        } else {
            ModuleRootModificationUtil.updateModel(module, updater)
        }
    }

    private fun findLibraryName(projectTable: LibraryTable, startName: String): Pair<Library?,String> {
        var index = 0
        while (true) {
            val name = if (index == 0) startName else startName + "_" + index
            val library = projectTable.getLibraryByName(name) ?: return Pair(null,name)
            if ((library as? LibraryEx)?.kind is ArendLibraryKind && library.getFiles(ArendConfigOrderRootType).firstOrNull()?.parent?.name == startName) {
                return Pair(library,name)
            }
            index++
        }
    }

    fun updateFromIDEA(config: ArendModuleConfiguration, state: ModuleConfigurationState) {
        val newLibrariesRoot = config.librariesRoot
        val reload = librariesRoot != newLibrariesRoot
        var updateYAML = false

        val newDependencies = config.dependencies
        if (dependencies != newDependencies) {
            updateDependencies(newDependencies, !reload)
            updateIdeaDependencies(state)
            updateYAML = true
        }

        val newSourcesDir = config.sourcesDir
        if (sourcesDir != newSourcesDir) {
            sourcesDir = newSourcesDir
            updateYAML = true
        }

        val newBinariesDir = config.flaggedBinariesDir
        if (flaggedBinariesDir != newBinariesDir) {
            updateYAML = true
        }
        withBinaries = config.withBinaries
        binariesDirectory = config.binariesDirectory

        if (updateYAML) yamlFile?.write {
            sourcesDir = newSourcesDir
            binariesDir = newBinariesDir
            dependencies = newDependencies
        }

        if (reload) {
            librariesRoot = newLibrariesRoot
            project.reload()
        }
    }

    companion object {
        fun getConfig(module: Module): LibraryConfig {
            if (ArendModuleType.has(module)) {
                val service = ModuleServiceManager.getService(module, ArendModuleConfigService::class.java)
                if (service != null) {
                    return service
                }
                NotificationErrorReporter.ERROR_NOTIFICATIONS.createNotification("Failed to get ArendModuleConfigService for $module", NotificationType.ERROR).notify(module.project)
            }
            return EmptyLibraryConfig(module.name, module.project)
        }

        fun getInstance(module: Module?) =
            if (module != null && ArendModuleType.has(module)) ModuleServiceManager.getService(module, ArendModuleConfigService::class.java) else null
    }
}