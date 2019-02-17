package org.arend.module.util

import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.layout.panel
import org.arend.module.config.DEFAULT_BINARIES_DIR
import org.arend.module.config.DEFAULT_SOURCES_DIR
import javax.swing.JComponent
import javax.swing.JTextField

class ArendModuleWizardStep(private val context: WizardContext) : ModuleWizardStep() {
    private var sourceDirTextField: JTextField? = null
    private var binariesDirTextField: JTextField? = null

    private fun getModuleRoot(): String? = (context.projectBuilder as? ArendModuleBuilder)?.moduleFileDirectory

    override fun getComponent(): JComponent {
        val moduleRoot = getModuleRoot() ?: return panel {}
        sourceDirTextField = JTextField(FileUtil.join(moduleRoot, DEFAULT_SOURCES_DIR))
        binariesDirTextField = JTextField(FileUtil.join(moduleRoot, DEFAULT_BINARIES_DIR))
        return panel {
            row("Sources directory: ") { wrapTextField(sourceDirTextField)() }
            row("Binaries directory: ") { wrapTextField(binariesDirTextField)() }
        }
    }

    private fun wrapTextField(textField: JTextField?): JComponent {
        val textFieldWithButton = TextFieldWithBrowseButton(textField)
        textFieldWithButton.addBrowseFolderListener("", "", context.project, FileChooserDescriptor(false, true, false, false, false, false))
        return textFieldWithButton
    }

    override fun updateDataModel() {
        val projectBuilder = context.projectBuilder as? ArendModuleBuilder ?: return
        val sourceText = sourceDirTextField?.text ?: return
        val binariesText = binariesDirTextField?.text ?: return
        projectBuilder.addModuleConfigurationUpdater(ArendModuleConfigurationUpdater(sourceText, binariesText))
    }
}
