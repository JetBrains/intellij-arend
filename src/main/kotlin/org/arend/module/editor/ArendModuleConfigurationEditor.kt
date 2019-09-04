package org.arend.module.editor

import com.intellij.openapi.module.ModuleConfigurationEditor
import org.arend.module.config.ArendModuleConfigService
import javax.swing.JComponent


class ArendModuleConfigurationEditor(private val moduleConfig: ArendModuleConfigService) : ModuleConfigurationEditor {
    private var view: ArendModuleConfigurationView? = null

    override fun getDisplayName() = "Arend module configuration"

    override fun isModified() = view?.isModified(moduleConfig) == true

    override fun apply() {
        view?.apply(moduleConfig)
    }

    override fun reset() {
        view?.reset(moduleConfig)
    }

    override fun createComponent(): JComponent? {
        view = ArendModuleConfigurationView(moduleConfig.project, moduleConfig.name)
        view?.reset(moduleConfig)
        return view?.createComponent()
    }
}