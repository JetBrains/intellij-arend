package org.vclang

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.vclang.module.util.isVcModule

val Project.vcModules: List<Module>
    get() = runReadAction { ModuleManager.getInstance(this).modules.filter { it.isVcModule } }
