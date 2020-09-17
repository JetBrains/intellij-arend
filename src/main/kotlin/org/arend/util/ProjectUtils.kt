package org.arend.util

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.arend.module.ArendModuleType
import org.arend.module.config.ArendModuleConfigService
import org.arend.module.config.ExternalLibraryConfig
import org.arend.typechecking.ArendExtensionChangeListener
import org.arend.typechecking.ArendTypechecking
import org.arend.typechecking.TypeCheckingService
import org.jetbrains.yaml.psi.YAMLFile
import java.nio.file.Path

val Project.arendModules: List<Module>
    get() = runReadAction { ModuleManager.getInstance(this).modules.filter { ArendModuleType.has(it) } }

fun Project.findConfigInZip(zipFile: VirtualFile): YAMLFile? {
    val zipRoot = JarFileSystem.getInstance().getJarRootForLocalFile(zipFile) ?: return null
    val configFile = zipRoot.findChild(FileUtils.LIBRARY_CONFIG_FILE) ?: return null
    return PsiManager.getInstance(this).findFile(configFile) as? YAMLFile ?: return null
}

fun Project.findExternalLibrary(root: VirtualFile, libName: String): ExternalLibraryConfig? {
    root.findChild(libName + FileUtils.ZIP_EXTENSION)?.let { zip ->
        findConfigInZip(zip)?.let { return ExternalLibraryConfig(libName, it) }
    }

    val configFile = root.findChild(libName)?.findChild(FileUtils.LIBRARY_CONFIG_FILE) ?: return null
    val yaml = PsiManager.getInstance(this).findFile(configFile) as? YAMLFile ?: return null
    return ExternalLibraryConfig(libName, yaml)
}

fun Project.findExternalLibrary(root: Path, libName: String): ExternalLibraryConfig? {
    val dir = LocalFileSystem.getInstance().findFileByPath(root.toString()) ?: return null
    return findExternalLibrary(dir, libName)
}

fun Module.register() {
    val service = project.service<TypeCheckingService>()
    service.initialize()
    val config = ArendModuleConfigService.getInstance(this) ?: return
    config.copyFromYAML(false)
    service.libraryManager.loadLibrary(config.library, ArendTypechecking.create(project))
    service<ArendExtensionChangeListener>().initializeModule(config)
}