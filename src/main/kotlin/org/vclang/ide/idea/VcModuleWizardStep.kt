package org.vclang.ide.idea

import com.intellij.ide.util.importProject.ProjectDescriptor
import com.intellij.ide.util.projectWizard.ModuleBuilder.ModuleConfigurationUpdater
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.layout.panel
import org.vclang.ide.VcConstants
import javax.swing.JComponent

class VcModuleWizardStep(
        private val context: WizardContext,
        private val projectDescriptor: ProjectDescriptor? = null
) : ModuleWizardStep() {

    override fun getComponent(): JComponent = panel {}

    override fun updateDataModel() {
        val projectBuilder = context.projectBuilder
        if (projectBuilder is VcModuleBuilder) {
            projectBuilder.addModuleConfigurationUpdater(ConfigurationUpdater)
        } else {
            projectDescriptor?.modules?.firstOrNull()?.addConfigurationUpdater(ConfigurationUpdater)
        }
    }

    private object ConfigurationUpdater : ModuleConfigurationUpdater() {
        override fun update(module: Module, rootModel: ModifiableRootModel) {
            rootModel.inheritSdk()
            val contentEntry = rootModel.contentEntries.singleOrNull()
            if (contentEntry != null) {
                val projectRoot = contentEntry.file ?: return

                // TODO: createChildDirectory???
                (VcConstants.ProjectLayout.sources + VcConstants.ProjectLayout.tests)
                        .filter { projectRoot.findChild(it) == null }
                        .forEach { projectRoot.createChildDirectory(null, it) }

                fun makeVfsUrl(dirName: String): String = FileUtil.join(projectRoot.url, dirName)

                VcConstants.ProjectLayout.sources.forEach {
                    contentEntry.addSourceFolder(makeVfsUrl(it), false)
                }
                VcConstants.ProjectLayout.tests.forEach {
                    contentEntry.addSourceFolder(makeVfsUrl(it), true)
                }
                contentEntry.addExcludeFolder(makeVfsUrl(VcConstants.ProjectLayout.cache))
            }
        }
    }
}
