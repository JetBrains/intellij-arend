package org.arend.typechecking.execution

import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.panel
import org.arend.ArendFileTypeInstance
import org.arend.ext.module.ModulePath
import org.arend.module.AllArendFilesScope
import org.arend.module.AllModulesScope
import org.arend.module.ArendPreludeLibrary.Companion.PRELUDE
import org.arend.module.ArendPreludeScope
import org.arend.module.config.ArendModuleConfigService
import org.arend.naming.scope.EmptyScope
import org.arend.naming.scope.LexicalScope
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiModuleReferable
import org.arend.refactoring.move.ArendLongNameCodeFragment
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.execution.configurations.TypeCheckConfiguration
import org.arend.util.aligned
import javax.swing.JComponent

class TypeCheckRunConfigurationEditor(private val project: Project) : SettingsEditor<TypeCheckConfiguration>() {
    private val libraryComponent: EditorTextField
    private val isTestComponent: JBCheckBox
    private val modulePathComponent: EditorTextField
    private val definitionNameComponent: EditorTextField


    init {
        libraryComponent = EditorTextField(PsiDocumentManager.getInstance(project).getDocument(
            ArendLongNameCodeFragment(project, LIBRARY_TEXT, null, customScopeGetter = { AllModulesScope(project) })), project, ArendFileTypeInstance
        )
        isTestComponent = JBCheckBox()
        val moduleDocument = ArendLongNameCodeFragment(project, MODULE_TEXT, null, customScopeGetter = {
            val library = libraryComponent.text
            if (library == PRELUDE) {
                return@ArendLongNameCodeFragment ArendPreludeScope(project)
            }
            val module = ModuleManager.getInstance(project).findModuleByName(library)
            val arendModuleConfigService = ArendModuleConfigService.getInstance(module)
            arendModuleConfigService?.let { AllArendFilesScope(it, ModulePath(), isTest = isTestComponent.isSelected) } ?: EmptyScope.INSTANCE
        })
        modulePathComponent = EditorTextField(PsiDocumentManager.getInstance(project).getDocument(moduleDocument), project, ArendFileTypeInstance)
        definitionNameComponent = EditorTextField(PsiDocumentManager.getInstance(project).getDocument(ArendLongNameCodeFragment(project, DEFINITION_TEXT, null, customScopeGetter = {
            val library = libraryComponent.text
            val module = modulePathComponent.text
            if (library == PRELUDE && module == PRELUDE) {
                return@ArendLongNameCodeFragment project.service<TypeCheckingService>().preludeScope
            } else if (library == PRELUDE) {
                return@ArendLongNameCodeFragment EmptyScope.INSTANCE
            }
            val arendFile = (moduleDocument.scope.resolveName(module) as? PsiModuleReferable)?.modules?.get(0) as? ArendFile?
                ?: return@ArendLongNameCodeFragment EmptyScope.INSTANCE
            LexicalScope.opened(arendFile)
        })), project, ArendFileTypeInstance)
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
