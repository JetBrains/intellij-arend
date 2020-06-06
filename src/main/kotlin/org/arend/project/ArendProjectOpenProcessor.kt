package org.arend.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectOpenProcessor
import javax.swing.Icon

class ArendProjectOpenProcessor : ProjectOpenProcessor() {
    override fun getName(): String = ArendOpenProjectProvider.builder.name

    override fun getIcon(): Icon? = ArendOpenProjectProvider.builder.icon

    override fun canOpenProject(file: VirtualFile): Boolean = ArendOpenProjectProvider.canOpenProject(file)

    override fun doOpenProject(projectFile: VirtualFile, projectToClose: Project?, forceOpenInNewFrame: Boolean): Project? {
        return ArendOpenProjectProvider.openProject(projectFile, projectToClose, forceOpenInNewFrame)
    }

    override fun canImportProjectAfterwards() = true

    override fun importProjectAfterwards(project: Project, file: VirtualFile) {
        ArendOpenProjectProvider.linkToExistingProject(file, project)
    }
}
