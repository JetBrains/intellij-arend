package org.arend

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.arend.module.ArendModuleType
import org.arend.module.config.ExternalLibraryConfig
import org.arend.util.FileUtils
import org.jetbrains.yaml.psi.YAMLFile
import java.nio.file.Path
import java.nio.file.Paths

val Project.arendModules: List<Module>
    get() = runReadAction { ModuleManager.getInstance(this).modules.filter { ArendModuleType.has(it) } }

fun Project.findExternalLibrary(libName: String): ExternalLibraryConfig? {
    val homePath = ProjectRootManager.getInstance(this).projectSdk?.homePath ?: return null
    val yaml = findPsiFileByPath(Paths.get(homePath).resolve(Paths.get(libName, FileUtils.LIBRARY_CONFIG_FILE))) as? YAMLFile ?: return null
    return ExternalLibraryConfig(libName, yaml)
}

fun Project.findPsiFileByPath(path: Path): PsiFile? {
    val vFile = VirtualFileManager.getInstance().getFileSystem(LocalFileSystem.PROTOCOL).findFileByPath(path.toString()) ?: return null
    return PsiManager.getInstance(this).findFile(vFile)
}