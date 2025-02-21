package org.arend.typechecking.execution

import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.panel
import org.arend.ArendFileTypeInstance
import org.arend.refactoring.move.ArendLongNameCodeFragment
import org.arend.typechecking.execution.configurations.TypeCheckConfiguration
import org.arend.util.aligned
import javax.swing.JComponent

class TypeCheckRunConfigurationEditor(project: Project) : SettingsEditor<TypeCheckConfiguration>() {
    private val libraryComponent = EditorTextField(PsiDocumentManager.getInstance(project).getDocument(ArendLongNameCodeFragment(project, LIBRARY_TEXT, null)), project, ArendFileTypeInstance)
    private val isTestComponent = JBCheckBox()
    private val modulePathComponent: EditorTextField
    private val definitionNameComponent: EditorTextField


    init {
        val moduleDocument = ArendLongNameCodeFragment(project, MODULE_TEXT, null)
        modulePathComponent = EditorTextField(PsiDocumentManager.getInstance(project).getDocument(moduleDocument), project, ArendFileTypeInstance)
        definitionNameComponent = EditorTextField(PsiDocumentManager.getInstance(project).getDocument(ArendLongNameCodeFragment(project, DEFINITION_TEXT, null)), project, ArendFileTypeInstance)
    }

    override fun resetEditorFrom(configuration: TypeCheckConfiguration) {
        with(configuration.arendTypeCheckCommand) {
            libraryComponent.text = library
            isTestComponent.isSelected = isTest
            modulePathComponent.text = modulePath
            definitionNameComponent.text = definitionFullName
        }
    }

    override fun applyEditorTo(configuration: TypeCheckConfiguration) {
        configuration.arendTypeCheckCommand = TypeCheckCommand(
            libraryComponent.text,
            isTestComponent.isSelected,
            modulePathComponent.text,
            definitionNameComponent.text
        )
    }

    override fun createEditor(): JComponent = panel {
        aligned("$LIBRARY_TEXT:", libraryComponent)
        aligned("$IS_TEST_TEXT:", isTestComponent)
        aligned("$MODULE_TEXT:", modulePathComponent)
        aligned("$DEFINITION_TEXT:", definitionNameComponent)
    }

    companion object {
        private const val LIBRARY_TEXT = "Arend library"
        private const val IS_TEST_TEXT = "Search in the test directory"
        private const val MODULE_TEXT = "Arend module"
        private const val DEFINITION_TEXT = "Definition"
    }
}
