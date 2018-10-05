package org.arend.module.util

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import org.arend.module.ArendModuleType

class ArendModuleBuilder : ModuleBuilder() {
    private var moduleRoot: VirtualFile? = null

    companion object {
        val DEFAULT_SOURCE_DIR = "src"
        val DEFAULT_OUTPUT_DIR = ".output"

        fun toAbsolute(root: String, path: String): String = if (FileUtil.isAbsolute(path)) path else FileUtil.join(root, path)
        fun toRelative(root: String, path: String): String? {
            if (FileUtil.isAbsolute(path)) {
                if (!path.startsWith(root)) return null
                return path.substring(root.length + 1)
            }
            return path
        }
    }

    override fun getModuleType(): ModuleType<*>? = ArendModuleType.INSTANCE

    override fun isSuitableSdkType(sdkType: SdkTypeId?): Boolean = true


    override fun getCustomOptionsStep(
            context: WizardContext,
            parentDisposable: Disposable
    ): ModuleWizardStep = ArendProjectStructureDetector.DummyStep

            //ArendModuleWizardStep(context, null, moduleRoot).apply {
        //Disposer.register(parentDisposable, Disposable { this.disposeUIResources() })
    //}

    override fun createFinishingSteps(wizardContext: WizardContext, modulesProvider: ModulesProvider): Array<ModuleWizardStep> =
        arrayOf(ArendModuleWizardStep(wizardContext))

    override fun setupRootModel(modifiableRootModel: ModifiableRootModel) {
        val root = doAddContentEntry(modifiableRootModel)?.file ?: return
        modifiableRootModel.inheritSdk()
        root.refresh(false, true)
        val contentEntry = modifiableRootModel.contentEntries.singleOrNull()
        if (contentEntry != null) {
            moduleRoot = contentEntry.file
        }
    }
}
