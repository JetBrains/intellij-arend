package org.vclang

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import java.io.File

val Project.modules: Collection<Module>
    get() = ModuleManager.getInstance(this).modules.toList()

val Project.modulesWithVclangProject: Collection<Module> // TODO[library]
    get() = modules.filter {
        File(it.moduleFilePath)
                .walk()
                .any { it.extension == VcFileType.defaultExtension }
    }

fun Project.getPsiFileFor(file: VirtualFile?): PsiFile? =
        file?.let { PsiManager.getInstance(this).findFile(it) }

fun Project.getPsiDirectoryFor(file: VirtualFile?): PsiDirectory? =
        file?.let { PsiManager.getInstance(this).findDirectory(it) }
