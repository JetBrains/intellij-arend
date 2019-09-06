package org.arend.module.config

import com.intellij.notification.NotificationType
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleServiceManager
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
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
import java.io.File
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
        dependencies = newDependencies

        if (reload) {
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

        updateIdeaDependencies()
    }

    fun copyFromYAML(yaml: YAMLFile, update: Boolean) {
        val newDependencies = yaml.dependencies
        if (dependencies != newDependencies) {
            updateDependencies(newDependencies, update)
        }

        modules = yaml.modules
        flaggedBinariesDir = yaml.binariesDir
        sourcesDir = yaml.sourcesDir ?: ""
    }

    fun copyFromYAML() {
        copyFromYAML(yamlFile ?: return, true)
    }

    private class IdeaDependency(val name: String, val module: Module?, val library: Library?) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as IdeaDependency

            if (name != other.name) return false
            if (module != other.module) return false
            if (library != other.library) return false

            return true
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + (module?.hashCode() ?: 0)
            result = 31 * result + (library?.hashCode() ?: 0)
            return result
        }
    }

    private fun updateIdeaDependencies() {
        val librariesRoot = librariesRootDef ?: return

        val ideaDependencies = ArrayList<IdeaDependency>()
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
                        val tableModel = projectTable.modifiableModel
                        library = tableModel.createLibrary(pair.second)

                        val libModel = library.modifiableModel
                        if (libModel is LibraryEx.ModifiableModelEx) {
                            libModel.kind = ArendLibraryKind
                        }

                        libModel.addRoot(VfsUtil.pathToUrl(FileUtil.join(librariesRoot, dependency.name, FileUtils.LIBRARY_CONFIG_FILE)), ArendConfigOrderRootType)
                        project.findExternalLibrary(Paths.get(librariesRoot), dependency.name)?.sourcesPath?.let {
                            libModel.addRoot(VfsUtil.pathToUrl(it.toString()), OrderRootType.SOURCES)
                        }
                        runInEdt { runWriteAction {
                            libModel.commit()
                            tableModel.commit()
                        } }
                    }
                    library
                }

                ideaDependencies.add(IdeaDependency(dependency.name, null, library))
            } else {
                ideaDependencies.add(IdeaDependency(dependency.name, depModule, null))
            }
        }

        // Update the module-level library table
        val rootModel = runReadAction { ModuleRootManager.getInstance(module).modifiableModel }
        try {
            for (entry in rootModel.orderEntries) {
                val ideaDependency = (entry as? LibraryOrderEntry)?.library?.let { lib -> entry.name?.let { IdeaDependency(it, null, lib) } }
                    ?: entry.module?.let { IdeaDependency(it.name, it, null) }
                if (ideaDependency != null && !ideaDependencies.remove(ideaDependency)) {
                    rootModel.removeOrderEntry(entry)
                }
            }
            for (ideaDependency in ideaDependencies) {
                if (ideaDependency.library != null) {
                    rootModel.addLibraryEntry(ideaDependency.library)
                }
                if (ideaDependency.module != null) {
                    rootModel.addModuleOrderEntry(ideaDependency.module)
                }
            }
            runInEdt {
                if (!module.isDisposed) {
                    runWriteAction { rootModel.commit() }
                }
            }
        } finally {
            if (!rootModel.isDisposed) {
                rootModel.dispose()
            }
        }
    }

    private fun findLibraryName(projectTable: LibraryTable, startName: String): Pair<Library?,String> {
        var index = 0
        while (true) {
            val name = if (index == 0) startName else startName + "_" + index
            val library = projectTable.getLibraryByName(name) ?: return Pair(null,name)
            if (library.getFiles(ArendConfigOrderRootType).firstOrNull()?.parent?.name == startName) {
                return Pair(library,name)
            }
            index++
        }
    }

    fun updateFromIDEA(config: ArendModuleConfiguration) {
        val newLibrariesRoot = config.librariesRoot
        val reload = librariesRoot != newLibrariesRoot
        var updateYAML = false

        val newDependencies = config.dependencies
        if (dependencies != newDependencies) {
            updateDependencies(newDependencies, !reload)
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
        private val OrderEntry.module: Module?
            get() = when (this) {
                // is ModuleSourceOrderEntry -> rootModel.module
                is ModuleOrderEntry -> module
                else -> null
            }

        private val OrderEntry.name: String?
            get() = when (this) {
                // is ModuleSourceOrderEntry -> rootModel.module.name
                is ModuleOrderEntry -> moduleName
                is LibraryOrderEntry -> getRootUrls(ArendConfigOrderRootType).firstOrNull()?.let {
                    val path = VfsUtil.urlToPath(it)
                    val suffix = File.separator + FileUtils.LIBRARY_CONFIG_FILE
                    if (path.endsWith(suffix)) {
                        val parent = path.removeSuffix(suffix)
                        val index = parent.lastIndexOf('/')
                        if (index >= 0 && index + 1 < parent.length) {
                            parent.substring(index + 1, parent.length)
                        } else null
                    } else null
                }
                else -> null
            }

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