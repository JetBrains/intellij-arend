package org.arend.module.editor

import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.arend.module.ArendModuleBuilder
import org.arend.module.config.ArendModuleConfigurationUpdater
import org.arend.prelude.Prelude
import org.arend.settings.ArendProjectSettings
import org.arend.settings.ArendSettings
import org.arend.util.FileUtils

class ArendModuleWizardStep(project: Project?, private val builder: ArendModuleBuilder) : ModuleWizardStep() {
    private val view = ArendModuleConfigurationView(project, builder.moduleFileDirectory).apply {
        librariesRoot = project?.service<ArendProjectSettings>()?.librariesRoot ?: service<ArendSettings>().librariesRoot
        sourcesDir = FileUtils.DEFAULT_SOURCES_DIR
        withBinaries = true
        binariesDirectory = FileUtils.DEFAULT_BINARIES_DIR
        langVersionString = Prelude.VERSION.toString()
    }

    override fun getComponent() = view.createComponent()

    override fun updateDataModel() {
        builder.addModuleConfigurationUpdater(ArendModuleConfigurationUpdater(true).apply { copyFrom(view) })
    }
}
