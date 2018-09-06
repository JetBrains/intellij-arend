package com.jetbrains.arend.ide.module

import com.intellij.openapi.module.ModuleTypeManager
import com.jetbrains.arend.ide.ArdIcons
import com.jetbrains.arend.ide.module.util.ArdModuleBuilder
import javax.swing.Icon

class ArdModuleType : com.jetbrains.arend.ide.module.ArdModuleTypeBase(ID) {

    override fun getNodeIcon(isOpened: Boolean): Icon? = ArdIcons.AREND

    override fun createModuleBuilder(): ArdModuleBuilder = ArdModuleBuilder()

    override fun getDescription(): String = "Arend library"

    override fun getName(): String = "Arend"

    companion object {
        private const val ID = "AREND_MODULE"

        val INSTANCE: ArdModuleType by lazy {
            ModuleTypeManager.getInstance().findByID(ID) as ArdModuleType
        }
    }
}
