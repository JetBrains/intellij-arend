package org.arend.util

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.arend.module.ArendModuleType
import org.arend.module.ArendRawLibrary
import org.arend.module.config.ArendModuleConfigService
import org.arend.module.config.ExternalLibraryConfig
import org.arend.resolving.ArendResolveCache
import org.arend.typechecking.TypeCheckingService
import org.jetbrains.yaml.psi.YAMLFile
import java.nio.file.Path
import java.nio.file.Paths

val Project.arendModules: List<Module>
    get() = runReadAction { ModuleManager.getInstance(this).modules.filter { ArendModuleType.has(it) } }

fun Project.findExternalLibrary(root: Path, libName: String): ExternalLibraryConfig? {
    val yaml = findPsiFileByPath(root.resolve(Paths.get(libName, FileUtils.LIBRARY_CONFIG_FILE))) as? YAMLFile ?: return null
    return ExternalLibraryConfig(libName, yaml)
}

fun Project.findPsiFileByPath(path: Path): PsiFile? {
    val vFile = VirtualFileManager.getInstance().getFileSystem(LocalFileSystem.PROTOCOL).findFileByPath(path.toString()) ?: return null
    return PsiManager.getInstance(this).findFile(vFile)
}

fun Module.register() {
    val service = project.service<TypeCheckingService>()
    service.initialize()
    ArendModuleConfigService.getInstance(this)?.copyFromYAML()
    service.libraryManager.loadLibrary(ArendRawLibrary(this))
}

fun Project.reload() {
    service<ArendResolveCache>().clear()
    service<TypeCheckingService>().libraryManager.unload()
    for (module in arendModules) {
        module.register()
    }
}