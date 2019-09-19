package org.arend.module.editor

import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.arend.module.ArendModuleBuilder
import org.arend.module.config.ArendModuleConfigurationUpdater
import org.arend.prelude.Prelude
import org.arend.settings.ArendSettings

class ArendModuleWizardStep(project: Project?, private val builder: ArendModuleBuilder) : ModuleWizardStep() {
    private val view = ArendModuleConfigurationView(project, builder.moduleFileDirectory).apply {
        librariesRoot = service<ArendSettings>().librariesRoot
        sourcesDir = "src"
        withBinaries = true
        binariesDirectory = ".bin"
        langVersionString = Prelude.VERSION
    }

    override fun getComponent() = view.createComponent()

    override fun updateDataModel() {
        builder.addModuleConfigurationUpdater(ArendModuleConfigurationUpdater(true).apply { copyFrom(view) })
    }
}
