package org.arend.module

import com.intellij.openapi.module.ModuleTypeManager
import org.arend.ArendIcons
import org.arend.module.util.ArendModuleBuilder
import javax.swing.Icon

class ArendModuleType : ArendModuleTypeBase(ID) {

    override fun getNodeIcon(isOpened: Boolean): Icon? = ArendIcons.AREND

    override fun createModuleBuilder(): ArendModuleBuilder = ArendModuleBuilder()

    override fun getDescription(): String = "Arend library"

    override fun getName(): String = "Arend"

    /*
    override fun modifySettingsStep(settingsStep: SettingsStep, moduleBuilder: ModuleBuilder): ModuleWizardStep? {
        return object : ModuleWizardStep() {
            override fun updateDataModel() {

            }

            override fun getComponent(): JComponent? {
                return null
            }

            override fun validate(): Boolean {
                if (settingsStep is ProjectSettingsStep) {
                    if (!FileUtils.isLibraryName(settingsStep.moduleNameField.text)) {
                        throw ConfigurationException("Invalid library name")
                    }
                }
                return true
            }
        }
    }*/

    companion object {
        private const val ID = "AREND_MODULE"

        val INSTANCE: ArendModuleType by lazy {
            ModuleTypeManager.getInstance().findByID(ID) as ArendModuleType
        }
    }
}
