package org.arend.module

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.module.ModuleTypeManager
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import org.arend.ArendIcons
import org.arend.module.util.ArendModuleBuilder
import org.arend.module.util.ArendModuleWizardStep
import javax.swing.Icon

class ArendModuleType : ArendModuleTypeBase(ID) {

    override fun getNodeIcon(isOpened: Boolean): Icon? = ArendIcons.AREND

    override fun createModuleBuilder(): ArendModuleBuilder = ArendModuleBuilder()

    override fun getDescription(): String = "Arend library"

    override fun getName(): String = "Arend"

    companion object {
        private const val ID = "AREND_MODULE"

        val INSTANCE: ArendModuleType by lazy {
            ModuleTypeManager.getInstance().findByID(ID) as ArendModuleType
        }
    }
}
