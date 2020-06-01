package org.arend.project

import com.intellij.openapi.externalSystem.importing.AbstractOpenProjectProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectImportBuilder
import org.arend.util.FileUtils

object ArendOpenProjectProvider : AbstractOpenProjectProvider() {
    val builder: ArendProjectImportBuilder
        get() = ProjectImportBuilder.EXTENSIONS_POINT_NAME.findExtensionOrFail(ArendProjectImportBuilder::class.java)

    override fun isProjectFile(file: VirtualFile) = file.name == FileUtils.LIBRARY_CONFIG_FILE

    override fun linkAndRefreshProject(projectDirectory: String, project: Project) {
        try {
            builder.isUpdate = false
            builder.fileToImport = projectDirectory
            if (builder.validate(null, project)) {
                builder.commit(project, null, ModulesProvider.EMPTY_MODULES_PROVIDER)
            }
        }
        finally {
            builder.cleanup()
        }
    }
}