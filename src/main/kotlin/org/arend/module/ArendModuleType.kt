package org.arend.module

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.ModuleTypeManager
import org.arend.ArendIcons
import org.arend.module.util.ArendModuleBuilder
import javax.swing.Icon

class ArendModuleType : ArendModuleTypeBase(ID) {

    override fun getNodeIcon(isOpened: Boolean): Icon? = ArendIcons.AREND_MODULE

    override fun createModuleBuilder(): ArendModuleBuilder = ArendModuleBuilder()

    override fun getDescription(): String = "Arend library"

    override fun getName(): String = "Arend"

    companion object {
        private const val ID = "AREND_MODULE"

        val INSTANCE: ArendModuleType by lazy {
            ModuleTypeManager.getInstance().findByID(ID) as ArendModuleType
        }

        fun has(module: Module) = ModuleType.`is`(module, INSTANCE)
    }
}
