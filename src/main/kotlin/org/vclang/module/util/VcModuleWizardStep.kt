package org.vclang.module.util

import com.intellij.ide.util.importProject.ProjectDescriptor
import com.intellij.ide.util.projectWizard.ModuleBuilder.ModuleConfigurationUpdater
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.layout.panel
import com.jetbrains.jetpad.vclang.util.FileUtils
import javax.swing.JComponent

class VcModuleWizardStep(
        private val context: WizardContext,
        private val projectDescriptor: ProjectDescriptor? = null
) : ModuleWizardStep() {
    // TODO: Add config steps instead of the constant
    private companion object {
        private const val SOURCE_DIR = "src"
        private const val OUTPUT_DIR = ".output"
    }

    override fun getComponent(): JComponent = panel {}

    override fun updateDataModel() {
        val projectBuilder = context.projectBuilder
        if (projectBuilder is VcModuleBuilder) {
            projectBuilder.addModuleConfigurationUpdater(ConfigurationUpdater)
        } else {
            projectDescriptor?.modules?.firstOrNull()?.addConfigurationUpdater(ConfigurationUpdater)
        }
    }

    override fun isStepVisible() = false

    private object ConfigurationUpdater : ModuleConfigurationUpdater() {
        override fun update(module: Module, rootModel: ModifiableRootModel) {
            rootModel.inheritSdk()
            val contentEntry = rootModel.contentEntries.singleOrNull()
            if (contentEntry != null) {
                val projectRoot = contentEntry.file ?: return

                 if (projectRoot.findChild(SOURCE_DIR) == null) {
                     projectRoot.createChildDirectory(null, SOURCE_DIR)
                 }

                if (projectRoot.findChild(FileUtils.LIBRARY_CONFIG_FILE) == null) {
                    val vclFile = projectRoot.createChildData(projectRoot, FileUtils.LIBRARY_CONFIG_FILE)
                    vclFile.setBinaryContent("sourcesDir: $SOURCE_DIR".toByteArray())
                }
                contentEntry.addSourceFolder(FileUtil.join(projectRoot.url, SOURCE_DIR), false)
                contentEntry.addExcludeFolder(FileUtil.join(projectRoot.url, OUTPUT_DIR))
            }
        }
    }
}
