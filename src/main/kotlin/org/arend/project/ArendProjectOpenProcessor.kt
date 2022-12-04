package org.arend.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectOpenProcessor
import kotlinx.coroutines.runBlocking

class ArendProjectOpenProcessor : ProjectOpenProcessor() {
    override val name
        get() = ArendOpenProjectProvider.builder.name

    override val icon
        get() = ArendOpenProjectProvider.builder.icon

    override fun canOpenProject(file: VirtualFile): Boolean = ArendOpenProjectProvider.canOpenProject(file)

    override fun doOpenProject(virtualFile: VirtualFile, projectToClose: Project?, forceOpenInNewFrame: Boolean): Project? =
        runBlocking { ArendOpenProjectProvider.openProject(virtualFile, projectToClose, forceOpenInNewFrame) }

    override fun canImportProjectAfterwards() = true

    override fun importProjectAfterwards(project: Project, file: VirtualFile) {
        ArendOpenProjectProvider.linkToExistingProject(file, project)
    }
}
