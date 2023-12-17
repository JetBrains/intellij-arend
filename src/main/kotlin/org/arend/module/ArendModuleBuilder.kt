package org.arend.module

import com.intellij.CommonBundle
import com.intellij.ide.JavaUiBundle
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.SdkSettingsStep
import com.intellij.ide.util.projectWizard.SettingsStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.ui.Messages
import org.arend.module.editor.ArendModuleWizardStep
import org.arend.util.FileUtils

class ArendModuleBuilder : ModuleBuilder() {
    override fun getModuleType() = ArendModuleType

    override fun getCustomOptionsStep(context: WizardContext, parentDisposable: Disposable) =
        ArendModuleWizardStep(context.project, this)

    override fun modifySettingsStep(settingsStep: SettingsStep) =
        object : SdkSettingsStep(settingsStep, this, { isSuitableSdkType(it) }) {
            init {
                myJdkComboBox.setSelectedItem(myJdkComboBox.showNoneSdkItem())
            }

            /**
             * This method is copy-pasted from the base class with checks for `null` SDK omitted.
             */
            override fun validate(): Boolean {
                try {
                    myModel.apply(null, true)
                } catch (e: ConfigurationException) {
                    //IDEA-98382 We should allow Next step if user has wrong SDK
                    if (Messages.showDialog(
                            JavaUiBundle.message("dialog.message.0.do.you.want.to.proceed", e.messageHtml),
                            e.title,
                            arrayOf(CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText()),
                            1,
                            Messages.getWarningIcon()
                        ) != Messages.YES
                    ) {
                        return false
                    }
                }
                return true
            }
        }

    override fun isSuitableSdkType(sdkType: SdkTypeId?) =
        sdkType is JavaSdkType && !sdkType.isDependent

    override fun validateModuleName(moduleName: String): Boolean {
        if (!FileUtils.isLibraryName(moduleName)) {
            throw ConfigurationException("Invalid module name")
        }
        return true
    }

    override fun setupRootModel(modifiableRootModel: ModifiableRootModel) {
        modifiableRootModel.inheritSdk()
        doAddContentEntry(modifiableRootModel)
    }
}
