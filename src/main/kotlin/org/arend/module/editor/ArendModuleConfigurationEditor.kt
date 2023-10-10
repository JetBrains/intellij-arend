package org.arend.module.editor

import com.intellij.openapi.module.ModuleConfigurationEditor
import com.intellij.openapi.module.ModuleManager
import org.arend.module.config.ArendModuleConfigService
import javax.swing.JComponent


class ArendModuleConfigurationEditor(private val moduleConfig: ArendModuleConfigService) : ModuleConfigurationEditor {
    private val view = ArendModuleConfigurationView(moduleConfig.module)

    override fun getDisplayName() = "Arend configuration"

    override fun isModified() = !view.compare(moduleConfig)

    override fun apply() {
        view.let { moduleConfig.updateFromIDEA(it) }
    }

    override fun reset() {
        view.copyFrom(moduleConfig)
    }

    override fun createComponent(): JComponent {
        view.copyFrom(moduleConfig)
        return view.createComponent()
    }
}