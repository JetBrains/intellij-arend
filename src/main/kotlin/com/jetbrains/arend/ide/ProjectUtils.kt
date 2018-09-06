package com.jetbrains.arend.ide

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.jetbrains.arend.ide.module.util.isArdModule

val Project.vcModules: List<Module>
    get() = runReadAction { ModuleManager.getInstance(this).modules.filter { it.isArdModule } }
