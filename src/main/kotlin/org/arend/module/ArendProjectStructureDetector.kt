package org.arend.module

import com.intellij.ide.util.importProject.ModuleDescriptor
import com.intellij.ide.util.importProject.ProjectDescriptor
import com.intellij.ide.util.projectWizard.importSources.DetectedProjectRoot
import com.intellij.ide.util.projectWizard.importSources.DetectedSourceRoot
import com.intellij.ide.util.projectWizard.importSources.ProjectFromSourcesBuilder
import com.intellij.ide.util.projectWizard.importSources.ProjectStructureDetector
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

    override fun setupProjectStructure(roots: MutableCollection<DetectedProjectRoot>, projectDescriptor: ProjectDescriptor, builder: ProjectFromSourcesBuilder) {
        projectDescriptor.modules = ArrayList()
        for (root in roots) {
            val moduleDescriptor = ModuleDescriptor(root.directory, ArendModuleType, emptyList<DetectedSourceRoot>())
            moduleDescriptor.addConfigurationUpdater(ArendModuleConfigurationUpdater(false))
            projectDescriptor.modules.add(moduleDescriptor)
        }
    }
}
