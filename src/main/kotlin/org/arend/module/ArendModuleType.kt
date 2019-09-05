package org.arend.module

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import org.arend.ArendIcons

object ArendModuleType : ModuleType<ArendModuleBuilder>("AREND_MODULE") {
    override fun getNodeIcon(isOpened: Boolean) = ArendIcons.AREND_MODULE

    override fun createModuleBuilder() = ArendModuleBuilder()

    override fun getDescription() = "Arend library"

    override fun getName() = "Arend"

    fun has(module: Module?) = module != null && `is`(module, ArendModuleType)
}
