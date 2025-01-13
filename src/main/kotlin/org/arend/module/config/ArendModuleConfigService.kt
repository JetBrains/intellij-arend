package org.arend.module.config

import com.intellij.openapi.application.*
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleServiceManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import org.arend.ext.module.ModulePath
import org.arend.library.LibraryDependency
import org.arend.module.*
import org.arend.settings.ArendProjectSettings
import org.arend.util.*
import org.arend.yaml.*
import org.jetbrains.yaml.psi.YAMLFile


class ArendModuleConfigService(val module: Module) : LibraryConfig(module.project), ArendModuleConfiguration {
    private var synchronized = false
    var isInitialized = ApplicationManager.getApplication().isUnitTestMode

    fun synchronize() = synchronized(this) {
        val result = !synchronized
        synchronized = true
        result
    }

    override var librariesRoot = project.service<ArendProjectSettings>().librariesRoot
        set(value) {
            field = value
            project.service<ArendProjectSettings>().librariesRoot = value
        }

    override var sourcesDir = ""
    override var withBinaries = false
    override var binariesDirectory = ""
    override var testsDir = ""
    override var withExtensions = false
    override var extensionsDirectory = ""
    override var extensionMainClassData = ""
    override var modules: List<ModulePath>? = null
    override var dependencies: List<LibraryDependency> = emptyList()
    override var versionString: String = ""
    override var langVersionString: String = ""

    override val binariesDir: String?
        get() = flaggedBinariesDir

    override val extensionsDir: String?
        get() = flaggedExtensionsDir

    override fun getExtensionMainClass() = flaggedExtensionMainClass

    override val version: Version?
        get() = Version.fromString(versionString)

    override val langVersion: Range<Version>
        get() = VersionRange.parseVersionRange(langVersionString) ?: Range.unbound()

    override val root: VirtualFile?
        get() = if (module.isDisposed) null else ModuleRootManager.getInstance(module).contentEntries.firstOrNull()?.file

    override val name
        get() = module.name

    val librariesRootDef: String?
        get() = librariesRoot.ifEmpty {
            root?.parent?.path?.let { FileUtil.toSystemDependentName(it) }
        }

    val library = ArendRawLibrary(this)

    /* TODO[server2]
    private fun updateDependencies(newDependencies: List<LibraryDependency>, reload: Boolean, callback: () -> Unit) {
        val oldDependencies = dependencies
        dependencies = ArrayList(newDependencies)
        synchronized = false

        if (!reload) {
            return
        }

        var reloadLib = false
        for (dependency in oldDependencies) {
            if (!newDependencies.contains(dependency) && libraryManager.getRegisteredLibrary(dependency.name) != null) {
                reloadLib = true
                break
            }
        }

        if (reloadLib) {
            refreshLibrariesDirectory(project.service<ArendProjectSettings>().librariesRoot)
            project.service<TypeCheckingService>().reload(true)
            callback()
        } else ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Loading Arend libraries", false) {
            override fun run(indicator: ProgressIndicator) {
                refreshLibrariesDirectory(project.service<ArendProjectSettings>().librariesRoot)
                runReadAction {
                    val typechecking = ArendTypechecking.create(project)
                    for (dependency in newDependencies) {
                        if (!oldDependencies.contains(dependency)) {
                            var depLibrary = libraryManager.getRegisteredLibrary(dependency.name)
                            if (depLibrary == null) {
                                depLibrary = libraryManager.loadDependency(library, dependency.name, typechecking)
                            }
                            if (depLibrary != null) {
                                libraryManager.registerDependency(library, depLibrary)
                            }
                        }
                    }
                }
                callback()
            }
        })
    }
    */

    fun synchronizeDependencies(reload: Boolean) {
        synchronized = false
        ModuleSynchronizer.synchronizeModule(this, reload)
    }

    fun copyFromYAML(yaml: YAMLFile, update: Boolean) {
        /* TODO[server2]
        val newDependencies = yaml.dependencies
        if (dependencies != newDependencies) {
            updateDependencies(newDependencies, update) {
                ModuleSynchronizer.synchronizeModule(this, false)
            }
        } */

        modules = yaml.modules
        flaggedBinariesDir = yaml.binariesDir
        testsDir = yaml.testsDir
        val extDir = yaml.extensionsDir
        val extMain = yaml.extensionMainClass
        withExtensions = extDir != null && extMain != null
        if (extDir != null) {
            extensionsDirectory = extDir
        }
        if (extMain != null) {
            extensionMainClassData = extMain
        }
        sourcesDir = yaml.sourcesDir ?: ""
        versionString = yaml.version
        langVersionString = yaml.langVersion

        if (sourcesDir == binariesDirectory) {
            binariesDirectory = ""
            invokeLater {
                runUndoTransparentWriteAction {
                    yaml.binariesDir = ""
                }
            }
        }
        if (sourcesDir == testsDir) {
            testsDir = ""
            invokeLater {
                runUndoTransparentWriteAction {
                    yaml.testsDir = ""
                }
            }
        }
        if (binariesDirectory == testsDir) {
            testsDir = ""
            invokeLater {
                runUndoTransparentWriteAction {
                    yaml.testsDir = ""
                }
            }
        }
    }

    fun copyFromYAML(update: Boolean) {
        copyFromYAML(yamlFile ?: return, update)
    }

    fun saveSettings() {
        project.service<YamlFileService>().updateIdea(runReadAction { yamlFile?.virtualFile } ?: return, this)
    }

    fun updateFromIDEA(config: ArendModuleConfiguration) {
        val newLibrariesRoot = config.librariesRoot
        val reload = librariesRoot != newLibrariesRoot
        var updateYAML = false

        val newDependencies = config.dependencies
        if (dependencies != newDependencies) {
            // TODO[server2]: updateDependencies(newDependencies, !reload) {}
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

        val newTestsDir = config.testsDir
        if (testsDir != newTestsDir) {
            testsDir = newTestsDir
            updateYAML = true
        }

        val newExtensionsDir = config.flaggedExtensionsDir
        if (flaggedExtensionsDir != newExtensionsDir) {
            updateYAML = true
        }

        val newExtensionMainClass = config.flaggedExtensionMainClass
        if (flaggedExtensionMainClass != newExtensionMainClass) {
            updateYAML = true
        }
        withExtensions = config.withExtensions
        extensionsDirectory = config.extensionsDirectory
        extensionMainClassData = config.extensionMainClassData

        val newVersion = config.versionString
        if (versionString != newVersion) {
            versionString = newVersion
            updateYAML = true
        }

        val newLangVersion = config.langVersionString
        if (langVersionString != newLangVersion) {
            langVersionString = newLangVersion
            updateYAML = true
        }

        if (updateYAML) yamlFile?.write {
            langVersion = newLangVersion
            version = newVersion
            sourcesDir = newSourcesDir
            binariesDir = newBinariesDir
            testsDir = newTestsDir
            extensionsDir = newExtensionsDir
            extensionMainClass = newExtensionMainClass
            dependencies = newDependencies
        }

        if (reload) {
            synchronized = false
            librariesRoot = newLibrariesRoot
            ModuleSynchronizer.synchronizeModule(this, true)
        }
    }

    fun updateSourceDirFromIDEA(newSourcesDir: String) {
        if (sourcesDir != newSourcesDir) {
            sourcesDir = newSourcesDir
            yamlFile?.write {
                sourcesDir = newSourcesDir
            }
        }
    }

    fun updateTestDirFromIDEA(newTestDir: String) {
        if (testsDir != newTestDir) {
            testsDir = newTestDir
            yamlFile?.write {
                testsDir = newTestDir
            }
        }
    }

    fun updateBinDirFromIDEA(newBinDir: String) {
        if (binariesDirectory != newBinDir) {
            binariesDirectory = newBinDir
            yamlFile?.write {
                binariesDir = newBinDir
            }
        }
    }

    companion object {
        fun getInstance(module: Module?) =
            if (module != null && ArendModuleType.has(module)) ModuleServiceManager.getService(module, ArendModuleConfigService::class.java) else null
    }
}