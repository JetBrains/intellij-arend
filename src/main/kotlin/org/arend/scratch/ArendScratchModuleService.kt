package org.arend.scratch

import com.intellij.openapi.components.Service
import com.intellij.openapi.module.Module
import org.arend.psi.ArendFile

@Service(Service.Level.PROJECT)
class ArendScratchModuleService {
    private val scratchFilesToModules = mutableMapOf<ArendFile, Module?>()

    fun updateFileModule(file: ArendFile, module: Module?) {
        scratchFilesToModules[file] = module
    }

    fun getModule(file: ArendFile) = scratchFilesToModules[file]
}
