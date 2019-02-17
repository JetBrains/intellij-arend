package org.arend.module.util

import com.intellij.ide.util.importProject.ModuleDescriptor
import com.intellij.ide.util.importProject.ProjectDescriptor
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.importSources.DetectedProjectRoot
import com.intellij.ide.util.projectWizard.importSources.DetectedSourceRoot
import com.intellij.ide.util.projectWizard.importSources.ProjectFromSourcesBuilder
import com.intellij.ide.util.projectWizard.importSources.ProjectStructureDetector
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.layout.panel
import org.arend.module.ArendModuleType
import org.arend.module.config.binariesDir
import org.arend.module.config.sourcesDir
import org.arend.util.FileUtils
import org.jetbrains.yaml.psi.YAMLFile
import java.io.File
import java.util.*
import javax.swing.Icon
import javax.swing.JComponent

class ArendProjectStructureDetector : ProjectStructureDetector() {

    override fun detectRoots(
            dir: File,
            children: Array<out File>,
            base: File,
            result: MutableList<DetectedProjectRoot>
    ): DirectoryProcessingResult {
        val containsConfigFile = dir.listFiles().any { it.name == FileUtils.LIBRARY_CONFIG_FILE }
        if (containsConfigFile) {
            result.add(object : DetectedProjectRoot(dir) {
                override fun getRootTypeName(): String = "Arend"
            })
        }
        return DirectoryProcessingResult.PROCESS_CHILDREN
    }

    override fun setupProjectStructure(
            roots: MutableCollection<DetectedProjectRoot>,
            projectDescriptor: ProjectDescriptor,
            builder: ProjectFromSourcesBuilder
    ) {
        projectDescriptor.modules = ArrayList()
        for (root in roots) {
            val moduleDescriptor = ModuleDescriptor(
                    root.directory,
                    ArendModuleType.INSTANCE,
                    emptyList<DetectedSourceRoot>()
            )
            moduleDescriptor.addConfigurationUpdater(ConfigurationUpdater)
            projectDescriptor.modules.add(moduleDescriptor)
        }
    }


    override fun createWizardSteps(
            builder: ProjectFromSourcesBuilder,
            projectDescriptor: ProjectDescriptor,
            stepIcon: Icon?
    ): List<ModuleWizardStep> = listOf(DummyStep)

    private object ConfigurationUpdater : ArendModuleConfigurationUpdater() {

        override fun sourceDir(moduleRoot: VirtualFile, project: Project): String {
            val virtualFile = moduleRoot.findChild(FileUtils.LIBRARY_CONFIG_FILE) ?: return super.sourceDir(moduleRoot, project)
            return (PsiManager.getInstance(project).findFile(virtualFile) as? YAMLFile)?.sourcesDir ?: super.sourceDir(moduleRoot, project)
        }

        override fun binariesDir(moduleRoot: VirtualFile, project: Project): String {
            val virtualFile = moduleRoot.findChild(FileUtils.LIBRARY_CONFIG_FILE) ?: return super.binariesDir(moduleRoot, project)
            return (PsiManager.getInstance(project).findFile(virtualFile) as? YAMLFile)?.binariesDir ?: super.binariesDir(moduleRoot, project)
        }

    }

    object DummyStep : ModuleWizardStep() {
        override fun updateDataModel() {

        }

        override fun getComponent(): JComponent = panel {}

        override fun isStepVisible(): Boolean {
            return false
        }
    }
}
