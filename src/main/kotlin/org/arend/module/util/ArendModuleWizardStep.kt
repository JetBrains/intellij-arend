package org.arend.module.util

import com.intellij.ide.util.importProject.ProjectDescriptor
import com.intellij.ide.util.projectWizard.ModuleBuilder.ModuleConfigurationUpdater
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.layout.panel
import org.arend.util.FileUtils
import javax.swing.JComponent

class ArendModuleWizardStep(
        private val context: WizardContext,
        private val projectDescriptor: ProjectDescriptor? = null
) : ModuleWizardStep() {
    // TODO: Add config steps instead of the constant


    override fun getComponent(): JComponent = panel {}

    override fun updateDataModel() {
        val projectBuilder = context.projectBuilder
        if (projectBuilder is ArendModuleBuilder) {
            projectBuilder.addModuleConfigurationUpdater(ArendModuleConfigurationUpdater())
        } else {
            projectDescriptor?.modules?.firstOrNull()?.addConfigurationUpdater(ArendModuleConfigurationUpdater())
        }
    }

    override fun isStepVisible() = false
}
