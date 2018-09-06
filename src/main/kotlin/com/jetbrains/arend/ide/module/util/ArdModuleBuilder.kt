package com.jetbrains.arend.ide.module.util

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.Disposer
import com.jetbrains.arend.ide.module.ArdModuleType

class ArdModuleBuilder : ModuleBuilder() {

    override fun getModuleType(): ModuleType<*>? = ArdModuleType.INSTANCE

    override fun isSuitableSdkType(sdkType: SdkTypeId?): Boolean = true

    override fun getCustomOptionsStep(
            context: WizardContext,
            parentDisposable: Disposable
    ): ModuleWizardStep = ArdModuleWizardStep(context).apply {
        Disposer.register(parentDisposable, Disposable { this.disposeUIResources() })
    }

    override fun setupRootModel(modifiableRootModel: ModifiableRootModel) {
        val root = doAddContentEntry(modifiableRootModel)?.file ?: return
        modifiableRootModel.inheritSdk()
        root.refresh(false, true)
    }
}
