package org.arend.module.config

import com.intellij.notification.NotificationType
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleServiceManager
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.arend.arendModules
import org.arend.findExternalLibrary
import org.arend.findPsiFileByPath
import org.arend.library.LibraryDependency
import org.arend.module.ArendModuleType
import org.arend.module.ModulePath
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.util.FileUtils
import org.jetbrains.yaml.psi.YAMLFile
import java.nio.file.Paths


class ArendModuleConfigService(private val module: Module) : LibraryConfig(module.project) {
    override var sourcesDir: String? = null
    override var outputDir: String? = null
    override var modules: List<ModulePath>? = null
    override var dependencies: List<LibraryDependency> = emptyList()

    val root
        get() = ModuleRootManager.getInstance(module).contentEntries.firstOrNull()?.file

    override val rootPath
        get() = root?.let { Paths.get(it.path) }

    override val name
        get() = module.name

    override val sourcesDirFile: VirtualFile?
        get() {
            val dir = sourcesDir ?: return root
            val root = root
            val path = when {
                root != null -> Paths.get(root.path).resolve(dir).toString()
                Paths.get(dir).isAbsolute -> dir
                else -> return null
            }
            return VirtualFileManager.getInstance().getFileSystem(LocalFileSystem.PROTOCOL).findFileByPath(path)
        }

    fun updateFromYAML() {
        val yaml = rootPath?.resolve(FileUtils.LIBRARY_CONFIG_FILE)?.let { module.project.findPsiFileByPath(it) as? YAMLFile }
        sourcesDir = yaml?.sourcesDir
        outputDir = yaml?.outputDir
        modules = yaml?.modules
        dependencies = yaml?.dependencies ?: emptyList()
        updateIdea()
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

    private fun updateIdea() {
        val ideaDependencies = ArrayList<IdeaDependency>()
        val arendModules = HashMap<String,Module>()
        for (depModule in project.arendModules) {
            arendModules[depModule.name] = depModule
        }

        // Locate dependencies and create libraries in the project-level table if necessary
        val projectTable = LibraryTablesRegistrar.getInstance().getLibraryTable(module.project)
        for (dependency in dependencies) {
            val depModule = arendModules[dependency.name]
            if (depModule == module) {
                continue
            }

            if (depModule == null) {
                val library = runReadAction {
                    var library = projectTable.getLibraryByName(dependency.name)
                    if (library == null) {
                        val libConfig = module.project.findExternalLibrary(dependency.name)
                        if (libConfig != null) {
                            val tableModel = projectTable.modifiableModel
                            library = tableModel.createLibrary(dependency.name)
                            val libModel = library.modifiableModel
                            val srcDir = libConfig.sourcesPath
                            if (srcDir != null) {
                                libModel.addRoot(VfsUtil.pathToUrl(srcDir.toString()), OrderRootType.SOURCES)
                            }
                            val outDir = libConfig.outputPath
                            if (outDir != null) {
                                libModel.addRoot(VfsUtil.pathToUrl(outDir.toString()), OrderRootType.CLASSES)
                            }
                            runWriteAction {
                                libModel.commit()
                                tableModel.commit()
                            }
                        }
                    }
                    library
                }

                if (library != null) {
                    ideaDependencies.add(IdeaDependency(dependency.name, null, library))
                }
            } else {
                ideaDependencies.add(IdeaDependency(dependency.name, depModule, null))
            }
        }

        if (ideaDependencies.isEmpty()) {
            return
        }

        // Libraries to be removed from the project-level library table
        val librariesToRemove = ArrayList<Library>()

        // Update the module-level library table
        ModuleRootModificationUtil.updateModel(module) { rootModel ->
            for (entry in rootModel.orderEntries) {
                val ideaDependency = (entry as? LibraryOrderEntry)?.library?.let { lib -> lib.name?.let { IdeaDependency(it, null, lib) } } ?:
                (entry as? ModuleOrderEntry)?.module?.let { IdeaDependency(it.name, it, null) }
                if (ideaDependency != null && !ideaDependencies.remove(ideaDependency)) {
                    rootModel.removeOrderEntry(entry)
                    if (ideaDependency.library != null) {
                        librariesToRemove.add(ideaDependency.library)
                    }
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
        }

        if (librariesToRemove.isEmpty()) {
            return
        }

        // Do not remove libraries which are used in other modules
        for (arendModule in arendModules.values) {
            if (arendModule != module) {
                for (dependency in getInstance(arendModule).dependencies) {
                    if (!arendModules.containsKey(dependency.name) && librariesToRemove.any { it.name == dependency.name }) {
                        val library = projectTable.getLibraryByName(dependency.name)
                        if (library != null && librariesToRemove.remove(library) && librariesToRemove.isEmpty()) {
                            return
                        }
                    }
                }
            }
        }

        // Remove unused libraries from the project-level library table
        val model = projectTable.modifiableModel
        for (library in librariesToRemove) {
            model.removeLibrary(library)
        }
        runWriteAction { model.commit() }
    }

    fun updateFromIdea() {

    }

    companion object {
        fun getInstance(module: Module): LibraryConfig {
            if (ArendModuleType.has(module)) {
                val service = ModuleServiceManager.getService(module, ArendModuleConfigService::class.java)
                if (service != null) {
                    return service
                }
                NotificationErrorReporter.ERROR_NOTIFICATIONS.createNotification("Failed to get ArendModuleConfigService for $module", NotificationType.ERROR)
            }
            return EmptyLibraryConfig(module.name, module.project)
        }
    }
}