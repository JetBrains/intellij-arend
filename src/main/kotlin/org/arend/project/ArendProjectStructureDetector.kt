package org.arend.project

import com.intellij.ide.util.importProject.ModuleDescriptor
import com.intellij.ide.util.importProject.ProjectDescriptor
import com.intellij.ide.util.projectWizard.importSources.DetectedProjectRoot
import com.intellij.ide.util.projectWizard.importSources.DetectedSourceRoot
import com.intellij.ide.util.projectWizard.importSources.ProjectFromSourcesBuilder
import com.intellij.ide.util.projectWizard.importSources.ProjectStructureDetector
import org.arend.module.ArendModuleType
import org.arend.module.config.ArendModuleConfigurationUpdater
import org.arend.util.FileUtils
import java.io.File
import java.util.*

class ArendProjectStructureDetector : ProjectStructureDetector() {
    override fun getDetectorId() = "Arend"

    override fun detectRoots(dir: File, children: Array<out File>, base: File, result: MutableList<DetectedProjectRoot>): DirectoryProcessingResult {
        val containsConfigFile = children.any { it.name == FileUtils.LIBRARY_CONFIG_FILE }
        if (containsConfigFile) {
            result.add(object : DetectedProjectRoot(dir) {
                override fun getRootTypeName() = "Arend"
            })
        }
        return DirectoryProcessingResult.PROCESS_CHILDREN
    }

    fun setupProjectStructure(roots: List<File>, projectDescriptor: ProjectDescriptor) {
        projectDescriptor.modules = ArrayList()
        for (root in roots) {
            val moduleDescriptor = ModuleDescriptor(root, ArendModuleType.INSTANCE, emptyList<DetectedSourceRoot>())
            moduleDescriptor.addConfigurationUpdater(ArendModuleConfigurationUpdater(false))
            projectDescriptor.modules.add(moduleDescriptor)
        }
    }

    override fun setupProjectStructure(roots: MutableCollection<DetectedProjectRoot>, projectDescriptor: ProjectDescriptor, builder: ProjectFromSourcesBuilder) {
        setupProjectStructure(roots.map { it.directory }, projectDescriptor)
    }
}
