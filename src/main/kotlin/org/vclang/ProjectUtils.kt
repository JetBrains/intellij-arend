package org.vclang

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.jetbrains.jetpad.vclang.util.FileUtils
import java.io.File

val Project.vcModules: Collection<Module>
    get() {
        val allModules = ModuleManager.getInstance(this).modules
        val modulesWithLibraryFile = allModules.filter {
            val moduleFile = it.moduleFile ?: return@filter false
            moduleFile.parent?.findChild(moduleFile.nameWithoutExtension + FileUtils.LIBRARY_EXTENSION) != null
        }
        return if (modulesWithLibraryFile.isNotEmpty()) modulesWithLibraryFile else
            allModules.filter {
                File(FileUtil.toSystemDependentName(it.moduleFilePath)).parentFile
                    .walk()
                    .any { it.extension == VcFileType.defaultExtension }
            }
    }
