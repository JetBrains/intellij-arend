package org.vclang

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.jetbrains.jetpad.vclang.util.FileUtils
import org.vclang.module.util.containsVcFile

val Project.vcModules: Collection<Module>
    get() {
        val allModules = ModuleManager.getInstance(this).modules
        val modulesWithLibraryFile = allModules.filter {
            val moduleFile = it.moduleFile ?: return@filter false
            moduleFile.parent?.findChild(moduleFile.nameWithoutExtension + FileUtils.LIBRARY_EXTENSION) != null
        }
        return if (modulesWithLibraryFile.isNotEmpty()) modulesWithLibraryFile else allModules.filter { it.containsVcFile }
    }
