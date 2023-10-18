package org.arend.module.editor

import com.intellij.openapi.module.ModuleConfigurationEditor
import org.arend.actions.addNewDirectory
import org.arend.actions.removeOldDirectory
import org.arend.module.config.ArendModuleConfigService
import org.jetbrains.jps.model.java.JavaSourceRootType
import javax.swing.JComponent


class ArendModuleConfigurationEditor(private val moduleConfig: ArendModuleConfigService) : ModuleConfigurationEditor {
    private val view = ArendModuleConfigurationView(moduleConfig.module)

    override fun getDisplayName() = "Arend configuration"

    override fun isModified() = !view.compare(moduleConfig)

    override fun apply() {
        view.let {
            val module = moduleConfig.module
            if (moduleConfig.sourcesDir != view.sourcesDir) {
                removeOldDirectory(module, moduleConfig.sourcesDirFile, moduleConfig, JavaSourceRootType.SOURCE)
            }
            if (moduleConfig.testsDir != view.testsDir) {
                removeOldDirectory(module, moduleConfig.sourcesDirFile, moduleConfig, JavaSourceRootType.TEST_SOURCE)
            }
            if (moduleConfig.sourcesDir != view.sourcesDir) {
                addNewDirectory(view.sourcesDir, moduleConfig, JavaSourceRootType.SOURCE)
            }
            if (moduleConfig.testsDir != view.testsDir) {
                addNewDirectory(view.testsDir, moduleConfig, JavaSourceRootType.TEST_SOURCE)
            }
            moduleConfig.updateFromIDEA(it)
        }
    }

    override fun reset() {
        view.copyFrom(moduleConfig)
    }

    override fun createComponent(): JComponent {
        view.copyFrom(moduleConfig)
        return view.createComponent()
    }
}