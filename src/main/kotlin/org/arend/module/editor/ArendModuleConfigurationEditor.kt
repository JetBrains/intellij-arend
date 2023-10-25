package org.arend.module.editor

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleConfigurationEditor
import com.intellij.openapi.module.ModuleServiceManager
import org.arend.actions.addNewDirectory
import org.arend.actions.removeOldDirectory
import org.arend.module.ArendModuleType
import org.arend.module.config.ArendModuleConfigService
import org.jetbrains.jps.model.java.JavaSourceRootType
import javax.swing.JComponent


class ArendModuleConfigurationEditor(private val module: Module) : ModuleConfigurationEditor {
    private val view = ArendModuleConfigurationView.getInstance(module) ?: ArendModuleConfigurationView(module)
    private val moduleConfig = ArendModuleConfigService.getInstance(module) ?: ArendModuleConfigService(module)

    override fun getDisplayName() = "Arend configuration"

    override fun isModified() = !view.compare(moduleConfig)

    override fun apply() {
        view.let {
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

    companion object {
        fun getInstance(module: Module?) =
            if (module != null && ArendModuleType.has(module)) ModuleServiceManager.getService(module, ArendModuleConfigurationEditor::class.java) else null
    }
}