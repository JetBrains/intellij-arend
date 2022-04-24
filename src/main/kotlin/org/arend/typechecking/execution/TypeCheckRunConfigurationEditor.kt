package org.arend.typechecking.execution

import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.panel
import org.arend.typechecking.execution.configurations.TypeCheckConfiguration
import org.arend.util.aligned
import javax.swing.JComponent
import javax.swing.JTextField

class TypeCheckRunConfigurationEditor(private val project: Project) : SettingsEditor<TypeCheckConfiguration>() {
    // TODO: replace text fields with some structure browser
    private val libraryComponent = JTextField()
    private val modulePathComponent = JTextField()
    private val definitionNameComponent = JTextField()

    override fun resetEditorFrom(configuration: TypeCheckConfiguration) {
        with(configuration.arendTypeCheckCommand) {
            libraryComponent.text = library
            modulePathComponent.text = modulePath
            definitionNameComponent.text = definitionFullName
        }
    }

    override fun applyEditorTo(configuration: TypeCheckConfiguration) {
        configuration.arendTypeCheckCommand = TypeCheckCommand(
            libraryComponent.text,
            modulePathComponent.text,
            definitionNameComponent.text
        )
    }

    override fun createEditor(): JComponent = panel {
        aligned("Arend library:", libraryComponent)
        aligned("Arend module:", modulePathComponent)
        aligned("Definition:", definitionNameComponent)
    }
}
