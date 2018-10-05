package org.arend.module.util

import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.layout.panel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField

class ArendModuleWizardStep(
        private val context: WizardContext//,
//        private val projectDescriptor: ProjectDescriptor? = null
) : ModuleWizardStep() {
    private var sourceDirTextField: JTextField? = null
    private var outputDirTextField: JTextField? = null

    private fun getModuleRoot(): String? = (context.projectBuilder as? ArendModuleBuilder)?.moduleFileDirectory

    override fun getComponent(): JComponent {
        sourceDirTextField = getModuleRoot()?.let { JTextField(ArendModuleBuilder.toAbsolute(it, ArendModuleBuilder.DEFAULT_SOURCE_DIR)) }
        outputDirTextField = getModuleRoot()?.let { JTextField(ArendModuleBuilder.toAbsolute(it, ArendModuleBuilder.DEFAULT_OUTPUT_DIR)) }
        return getModuleRoot()?.let {
            panel {
                row("Sources directory: ") {
                    TextFieldWithBrowseButton(sourceDirTextField).let {
                        it.addBrowseFolderListener("", "", context.project,
                                FileChooserDescriptor(false, true, false, false, false, false)); it
                    }()
                }
                row("Output directory: ") {
                    TextFieldWithBrowseButton(outputDirTextField).let {
                        it.addBrowseFolderListener("", "", context.project,
                                FileChooserDescriptor(false, true, false, false, false, false)); it
                    }()
                }
            }
        } ?: panel {}
    }

    override fun updateDataModel() {
        val projectBuilder = context.projectBuilder
        if (projectBuilder is ArendModuleBuilder && sourceDirTextField != null && outputDirTextField != null) {
            projectBuilder.addModuleConfigurationUpdater(ArendModuleConfigurationUpdater(sourceDirTextField!!.text, outputDirTextField!!.text))
        }
        //else {
         //   projectDescriptor?.modules?.firstOrNull()?.addConfigurationUpdater(ArendModuleConfigurationUpdater())
       // }
    }
}
