package org.arend.module

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.roots.ModifiableRootModel
import org.arend.module.editor.ArendModuleWizardStep
import org.arend.util.FileUtils

class ArendModuleBuilder : ModuleBuilder() {
    override fun getModuleType() = ArendModuleType

    override fun getCustomOptionsStep(context: WizardContext, parentDisposable: Disposable) =
        ArendModuleWizardStep(context.project, this)

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
