package org.vclang.module

import com.intellij.openapi.module.ModuleTypeManager
import org.vclang.VcIcons
import org.vclang.module.util.VcModuleBuilder
import javax.swing.Icon

class VcModuleType : VcModuleTypeBase(ID) {

    override fun getNodeIcon(isOpened: Boolean): Icon? = VcIcons.VCLANG

    override fun createModuleBuilder(): VcModuleBuilder = VcModuleBuilder()

    override fun getDescription(): String = "Vclang library"

    override fun getName(): String = "Vclang"

    companion object {
        private const val ID = "VCLANG_MODULE"

        val INSTANCE: VcModuleType by lazy {
            ModuleTypeManager.getInstance().findByID(ID) as VcModuleType
        }
    }
}
