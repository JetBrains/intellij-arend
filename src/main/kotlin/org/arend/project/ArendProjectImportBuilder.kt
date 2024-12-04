package org.arend.project

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.util.projectWizard.importSources.ProjectStructureDetector
import com.intellij.ide.util.projectWizard.importSources.impl.ProjectFromSourcesBuilderImpl
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.packaging.artifacts.ModifiableArtifactModel
import com.intellij.projectImport.ProjectImportBuilder
import org.arend.ArendIcons
import org.arend.util.FileUtils
import java.io.File

class ArendProjectImportBuilder : ProjectImportBuilder<String>() {
    private var myParameters: Parameters? = null
    private val parameters: Parameters
        get() = myParameters ?: Parameters().also { myParameters = it }

    override fun getIcon() = ArendIcons.AREND

    override fun getName(): String = "Arend project"

    override val isOpenProjectSettingsAfter
        get() = parameters.openProjectSettingsAfter

    override fun setOpenProjectSettingsAfter(on: Boolean) {
        parameters.openProjectSettingsAfter = on
    }

    override fun commit(project: Project, model: ModifiableModuleModel?, modulesProvider: ModulesProvider?, artifactModel: ModifiableArtifactModel?): List<Module>? {
        val rootPath = fileToImport ?: return null
        val detector = ProjectStructureDetector.EP_NAME.findExtensionOrFail(ArendProjectStructureDetector::class.java)
        val builder = ProjectFromSourcesBuilderImpl(WizardContext(project, project), modulesProvider ?: ModulesProvider.EMPTY_MODULES_PROVIDER)
        builder.baseProjectPath = rootPath
        detector.setupProjectStructure(listOf(File(rootPath)), builder.getProjectDescriptor(detector))
        return builder.commit(project, model, modulesProvider, artifactModel)
    }

    override fun cleanup() {
        super.cleanup()
        myParameters = null
    }

    override fun getList() = fileToImport?.let { listOf(it + "/" + FileUtils.LIBRARY_CONFIG_FILE) } ?: emptyList()

    override fun setList(list: List<String>) {
        if (list.isEmpty()) {
            parameters.doImport = false
        }
    }

    override fun isMarked(element: String) = parameters.doImport
}

private class Parameters {
    var doImport = true
    var openProjectSettingsAfter = false
}