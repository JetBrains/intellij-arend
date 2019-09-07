package org.arend.module.editor

import com.intellij.openapi.module.ModuleConfigurationEditor
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState
import org.arend.module.config.ArendModuleConfigService
import javax.swing.JComponent


class ArendModuleConfigurationEditor(private val moduleConfig: ArendModuleConfigService, private val state: ModuleConfigurationState) : ModuleConfigurationEditor {
    private var view: ArendModuleConfigurationView? = null

    override fun getDisplayName() = "Arend module configuration"

    override fun isModified() = view?.compare(moduleConfig) == false

    override fun apply() {
        view?.let { moduleConfig.updateFromIDEA(it, state) }
    }

    override fun reset() {
        view?.copyFrom(moduleConfig)
    }

    override fun createComponent(): JComponent? {
        view = ArendModuleConfigurationView(moduleConfig.project, moduleConfig.rootDir, moduleConfig.name)
        view?.copyFrom(moduleConfig)
        return view?.createComponent()
    }
}