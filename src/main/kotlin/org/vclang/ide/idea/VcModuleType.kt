package org.vclang.ide.idea

import com.intellij.openapi.module.ModuleTypeManager
import org.vclang.ide.icons.VcIcons
import javax.swing.Icon

class VcModuleType : VcModuleTypeBase(ID) {

    override fun getNodeIcon(isOpened: Boolean): Icon? = VcIcons.VCLANG

    override fun createModuleBuilder(): VcModuleBuilder = VcModuleBuilder()

    override fun getDescription(): String = "Vclang module"

    override fun getName(): String = "Vclang"

    companion object {
        private val ID = "VCLANG_MODULE"

        val INSTANCE: VcModuleType by lazy {
            ModuleTypeManager.getInstance().findByID(ID) as VcModuleType
        }
    }
}
