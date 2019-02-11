package org.arend.module.config

import com.intellij.notification.NotificationType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleServiceManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.arend.findPsiFileByPath
import org.arend.library.LibraryDependency
import org.arend.module.ArendModuleType
import org.arend.module.ModulePath
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.util.FileUtils
import org.jetbrains.yaml.psi.YAMLFile
import java.nio.file.Paths


class ArendModuleConfigService(private val module: Module) : LibraryConfig() {
    override var sourcesDir: String? = null
    override var outputDir: String? = null
    override var modules: List<ModulePath>? = null
    override var dependencies: List<LibraryDependency> = emptyList()

    init {
        val yaml = rootPath?.resolve(FileUtils.LIBRARY_CONFIG_FILE)?.let { module.project.findPsiFileByPath(it) as? YAMLFile }
        if (yaml != null) {
            sourcesDir = yaml.sourcesDir
            outputDir = yaml.outputDir
            modules = yaml.modules
            dependencies = yaml.dependencies
        }
    }

    private val root
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

    companion object {
        fun getInstance(module: Module): LibraryConfig {
            val isArendModule = ArendModuleType.has(module)
            val service = if (isArendModule) ModuleServiceManager.getService(module, ArendModuleConfigService::class.java) else null
            if (service == null) {
                NotificationErrorReporter.ERROR_NOTIFICATIONS.createNotification(if (isArendModule) "Failed to get ArendModuleConfigService for $module" else "$module is not an Arend module", NotificationType.ERROR)
            }
            return service ?: EmptyLibraryConfig(module.name)
        }
    }
}