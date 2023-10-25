package org.arend.module.config

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureExtension
import org.arend.module.editor.ArendModuleConfigurationView
import org.arend.util.allModules

class ArendModuleStructureExtension : ModuleStructureExtension() {
    override fun moduleRemoved(module: Module?) {
        val modules = module?.project?.allModules ?: emptyList()
        for (otherModule in modules) {
            if (module != otherModule) {
                ArendModuleConfigurationView.getInstance(otherModule)?.updateAvailableLibrariesAndDependencies()
            }
        }
    }
}
