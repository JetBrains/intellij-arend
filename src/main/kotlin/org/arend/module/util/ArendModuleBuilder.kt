package org.arend.module.util

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import org.arend.module.ArendModuleType
import org.arend.util.FileUtils

class ArendModuleBuilder : ModuleBuilder() {

    companion object {
        val DEFAULT_SOURCE_DIR = "src"
        val DEFAULT_OUTPUT_DIR = ".output"

        /*
        fun toAbsolute(root: String, path: String): String = if (FileUtil.isAbsolute(path)) path else FileUtil.join(root, path)
        fun toRelative(root: String, path: String): String? {
            if (FileUtil.isAbsolute(path)) {
                if (!path.startsWith(root)) return null
                return path.substring(root.length + 1)
            }
            return path
        }*/
    }

    override fun getModuleType(): ModuleType<*>? = ArendModuleType.INSTANCE

    override fun isSuitableSdkType(sdkType: SdkTypeId?): Boolean = true

    override fun getCustomOptionsStep(
            context: WizardContext,
            parentDisposable: Disposable
    ): ModuleWizardStep = ArendProjectStructureDetector.DummyStep

    override fun validateModuleName(moduleName: String): Boolean {
        if (!FileUtils.isLibraryName(moduleName)) {
            throw ConfigurationException("Invalid library name")
        }
        return true
    }

    override fun createFinishingSteps(wizardContext: WizardContext, modulesProvider: ModulesProvider): Array<ModuleWizardStep> =
        arrayOf(ArendModuleWizardStep(wizardContext))

    override fun setupRootModel(modifiableRootModel: ModifiableRootModel) {
        val root = doAddContentEntry(modifiableRootModel)?.file ?: return
        modifiableRootModel.inheritSdk()
        root.refresh(false, true)
    }
}
