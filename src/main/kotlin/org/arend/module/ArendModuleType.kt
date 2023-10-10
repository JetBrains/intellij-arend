package org.arend.module

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import org.arend.ArendIcons

class ArendModuleType : ModuleType<ArendModuleBuilder>("AREND_MODULE") {
    override fun getNodeIcon(isOpened: Boolean) = ArendIcons.AREND

    override fun createModuleBuilder() = ArendModuleBuilder()

    override fun getDescription() = "Arend library"

    override fun getName() = "Arend"

    companion object {
        fun has(module: Module?) = module != null && `is`(module, INSTANCE)

        @JvmField
        val INSTANCE = ArendModuleType()
    }
}
