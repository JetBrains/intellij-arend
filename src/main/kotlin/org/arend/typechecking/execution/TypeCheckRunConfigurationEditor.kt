package org.arend.typechecking.execution

import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.ui.components.Label
import com.intellij.ui.layout.LayoutBuilder
import com.intellij.ui.layout.Row
import com.intellij.ui.layout.panel
import org.arend.typechecking.execution.configurations.TypeCheckConfiguration
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
        labeledRow("Arend library:", libraryComponent) {
            libraryComponent()
        }
        labeledRow("Arend module:", modulePathComponent) {
            modulePathComponent()
        }
        labeledRow("Definition:", definitionNameComponent) {
            definitionNameComponent()
        }
    }

    private fun LayoutBuilder.labeledRow(
            labelText: String,
            component: JComponent,
            init: Row.() -> Unit
    ) {
        val label = Label(labelText)
        label.labelFor = component
        row(label) { init() }
    }
}
