package org.arend.module.util

import com.intellij.ide.util.importProject.ModuleDescriptor
import com.intellij.ide.util.importProject.ProjectDescriptor
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.importSources.DetectedProjectRoot
import com.intellij.ide.util.projectWizard.importSources.DetectedSourceRoot
import com.intellij.ide.util.projectWizard.importSources.ProjectFromSourcesBuilder
import com.intellij.ide.util.projectWizard.importSources.ProjectStructureDetector
import org.arend.ArendFileType
import org.arend.module.ArendModuleType
import java.io.File
import javax.swing.Icon

class ArendProjectStructureDetector : ProjectStructureDetector() {

    override fun detectRoots(
            dir: File,
            children: Array<out File>,
            base: File,
            result: MutableList<DetectedProjectRoot>
    ): DirectoryProcessingResult {
        val containsArendFile = dir
                .walk()
                .any { it.extension == ArendFileType.defaultExtension }
        if (containsArendFile) {
            result.add(object : DetectedProjectRoot(dir) {
                override fun getRootTypeName(): String = "Arend"
            })
        }
        return DirectoryProcessingResult.SKIP_CHILDREN
    }

    override fun setupProjectStructure(
            roots: MutableCollection<DetectedProjectRoot>,
            projectDescriptor: ProjectDescriptor,
            builder: ProjectFromSourcesBuilder
    ) {
        val root = roots.singleOrNull()
        if (root == null
                || builder.hasRootsFromOtherDetectors(this)
                || projectDescriptor.modules.isNotEmpty()) {
            return
        }

        val moduleDescriptor = ModuleDescriptor(
                root.directory,
                ArendModuleType.INSTANCE,
                emptyList<DetectedSourceRoot>()
        )
        projectDescriptor.modules = listOf(moduleDescriptor)
    }

    override fun createWizardSteps(
            builder: ProjectFromSourcesBuilder,
            projectDescriptor: ProjectDescriptor,
            stepIcon: Icon?
    ): List<ModuleWizardStep> = listOf(ArendModuleWizardStep(builder.context, projectDescriptor))
}
