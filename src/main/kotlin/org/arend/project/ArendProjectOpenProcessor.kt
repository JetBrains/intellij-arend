package org.arend.project

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.PlatformProjectOpenProcessor
import com.intellij.projectImport.ProjectImportBuilder
import com.intellij.projectImport.ProjectOpenProcessor
import org.arend.util.FileUtils

class ArendProjectOpenProcessor : ProjectOpenProcessor() {
    private val builder: ArendProjectImportBuilder
        get() = ProjectImportBuilder.EXTENSIONS_POINT_NAME.findExtensionOrFail(ArendProjectImportBuilder::class.java)

    override val name
        get() = builder.name

    override val icon
        get() = builder.icon

    private fun isArendConfig(file: VirtualFile) = file.name == FileUtils.LIBRARY_CONFIG_FILE

    override fun canOpenProject(file: VirtualFile): Boolean =
        if (file.isDirectory) file.children.any(::isArendConfig) else isArendConfig(file)

    override fun doOpenProject(virtualFile: VirtualFile, projectToClose: Project?, forceOpenInNewFrame: Boolean): Project? {
        val basedir = if (virtualFile.isDirectory) virtualFile else virtualFile.parent
        return PlatformProjectOpenProcessor.getInstance().doOpenProject(basedir, projectToClose, forceOpenInNewFrame)?.also {
            StartupManager.getInstance(it).runAfterOpened { runInEdt { importProjectAfterwards(it, virtualFile) } }
        }
    }

    override fun canImportProjectAfterwards() = true

    override fun importProjectAfterwards(project: Project, file: VirtualFile) {
        val builder = builder
        try {
            builder.setUpdate(false)
            builder.fileToImport = (if (file.isDirectory) file else file.parent).toNioPath().toString()
            if (builder.validate(null, project)) {
                builder.commit(project, null, ModulesProvider.EMPTY_MODULES_PROVIDER)
            }
        }
        finally {
            builder.cleanup()
        }
    }
}
