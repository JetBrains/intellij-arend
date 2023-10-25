package org.arend.module.editor

import com.intellij.openapi.module.ModuleConfigurationEditor
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationEditorProvider
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState

class ArendModuleConfigEditorProvider: ModuleConfigurationEditorProvider {
    override fun createEditors(state: ModuleConfigurationState): Array<ModuleConfigurationEditor> {
        val module = state.currentRootModel?.module
        return if (module != null) arrayOf(ArendModuleConfigurationEditor(module)) else emptyArray()
    }
}