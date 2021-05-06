package org.arend.module.editor

import com.intellij.openapi.module.ModuleConfigurationEditor
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationEditorProvider
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState
import org.arend.module.config.ArendModuleConfigService

class ArendModuleConfigEditorProvider: ModuleConfigurationEditorProvider {
    override fun createEditors(state: ModuleConfigurationState): Array<ModuleConfigurationEditor> {
        val moduleConfig = ArendModuleConfigService.getInstance(state.currentRootModel?.module)
        return if (moduleConfig != null) arrayOf(ArendModuleConfigurationEditor(moduleConfig)) else emptyArray()
    }
}