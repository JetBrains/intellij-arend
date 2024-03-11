package org.arend.module.editor

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleConfigurationEditor
import org.arend.actions.mark.DirectoryType.*
import org.arend.actions.mark.addMarkedDirectory
import org.arend.actions.mark.removeMarkedDirectory
import org.arend.module.config.ArendModuleConfigService
import javax.swing.JComponent


class ArendModuleConfigurationEditor(module: Module) : ModuleConfigurationEditor {
    private val view = ArendModuleConfigurationView.getInstance(module) ?: ArendModuleConfigurationView(module)
    private val moduleConfig = ArendModuleConfigService.getInstance(module) ?: ArendModuleConfigService(module)

    override fun getDisplayName() = "Arend configuration"

    override fun isModified() = !view.compare(moduleConfig)

    override fun apply() {
        view.let {
            if (moduleConfig.sourcesDir != view.sourcesDir) {
                removeMarkedDirectory(moduleConfig.sourcesDirFile, moduleConfig, SRC)
            }
            if (moduleConfig.testsDir != view.testsDir) {
                removeMarkedDirectory(moduleConfig.testsDirFile, moduleConfig, TEST_SRC)
            }
            if (moduleConfig.binariesDirectory != view.binariesDirectory) {
                removeMarkedDirectory(moduleConfig.binariesDirFile, moduleConfig, BIN)
            }

            if (moduleConfig.sourcesDir != view.sourcesDir) {
                addMarkedDirectory(view.sourcesDir, moduleConfig, SRC)
            }
            if (moduleConfig.testsDir != view.testsDir) {
                addMarkedDirectory(view.testsDir, moduleConfig, TEST_SRC)
            }
            if (moduleConfig.binariesDirectory != view.binariesDirectory) {
                addMarkedDirectory(view.binariesDirectory, moduleConfig, BIN)
            }
            it.dependencies = it.dependencies.filter { libraryDependency -> it.isModuleOrLibraryExists(libraryDependency.name) }
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