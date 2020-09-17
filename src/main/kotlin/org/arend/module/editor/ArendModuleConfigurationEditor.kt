package org.arend.module.editor

import com.intellij.openapi.module.ModuleConfigurationEditor
import org.arend.module.config.ArendModuleConfigService
import javax.swing.JComponent


class ArendModuleConfigurationEditor(private val moduleConfig: ArendModuleConfigService) : ModuleConfigurationEditor {
    private var view: ArendModuleConfigurationView? = null

    override fun getDisplayName() = "Arend configuration"

    override fun isModified() = view?.compare(moduleConfig) == false

    override fun apply() {
        view?.let { moduleConfig.updateFromIDEA(it) }
    }

    override fun reset() {
        view?.copyFrom(moduleConfig)
    }

    override fun createComponent(): JComponent? {
        view = ArendModuleConfigurationView(moduleConfig.project, moduleConfig.root?.let { if (it.isDirectory) it.path else null }, moduleConfig.name)
        view?.copyFrom(moduleConfig)
        return view?.createComponent()
    }
}