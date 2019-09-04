package org.arend.module.editor

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.panel
import org.arend.module.config.ArendModuleConfigService
import org.arend.util.checked
import org.arend.util.labeled
import javax.swing.BorderFactory


class ArendModuleConfigurationView(project: Project, name: String? = null) {
    private val sourcesTextField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener("Sources directory", "Select the directory in which the source files${if (name == null) "" else " of module $name"} are located", project, FileChooserDescriptorFactory.createSingleFolderDescriptor())
    }
    private val binariesSwitch = JBCheckBox("Save typechecker output to ", false)
    private val binariesTextField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener("Binaries directory", "Select the directory in which the binary files${if (name == null) "" else " of module $name"} will be put", project, FileChooserDescriptorFactory.createSingleFolderDescriptor())
    }
    private val libRootTextField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener("Path to libraries", "Select the directory in which dependencies${if (name == null) "" else " of module $name"} are located", project, FileChooserDescriptorFactory.createSingleFolderDescriptor())
    }

    fun isModified(moduleConfig: ArendModuleConfigService) =
        moduleConfig.sourcesDir != sourcesTextField.text ||
        moduleConfig.withBinaries != binariesSwitch.isSelected ||
        moduleConfig.binariesDirectory != binariesTextField.text ||
        moduleConfig.librariesRoot != libRootTextField.text

    fun apply(moduleConfig: ArendModuleConfigService) {
        moduleConfig.sourcesDir = sourcesTextField.text
        moduleConfig.withBinaries = binariesSwitch.isSelected
        moduleConfig.binariesDirectory = binariesTextField.text
        moduleConfig.librariesRoot = libRootTextField.text

        moduleConfig.updateFromIdea()
    }

    fun reset(moduleConfig: ArendModuleConfigService) {
        sourcesTextField.text = moduleConfig.sourcesDir
        binariesSwitch.isSelected = moduleConfig.withBinaries
        binariesTextField.text = moduleConfig.binariesDirectory
        libRootTextField.text = moduleConfig.librariesRoot
    }

    fun createComponent() = panel {
        labeled("Sources directory: ", sourcesTextField)
        checked(binariesSwitch, binariesTextField)

        titledRow("Libraries") {
            labeled("Path to libraries: ", libRootTextField)
        }
    }.apply {
        border = BorderFactory.createEmptyBorder(0, 10, 0, 10)
    }
}