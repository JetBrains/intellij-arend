package org.arend.editor

import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.panel
import org.arend.settings.ArendSettings
import org.arend.util.aligned
import org.arend.util.checked


class ArendSettingsView {
    private val arendSettings = service<ArendSettings>()

    // Background typechecking
    private val typecheckingMode = JBCheckBox("Enable background typechecking", true)

    // Other settings
    private val clauseLimitSwitch = JBCheckBox("Limit the maximum number of clauses generated at once: ", true)
    private val checkForUpdatesSwitch = JBCheckBox("Check for updates of arend-lib", true)
    private val clauseLimit = JBIntSpinner(10, 1, 1000)

    private val arendJarTextField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(TextBrowseFolderListener(FileChooserDescriptor(false, false, true, true, false, false)))
        //addBrowseFolderListener("Path to Arend jar for debugger", "Specify the path to Arend console application jar to be used in debugger", , FileChooserDescriptorFactory.createSingleFileDescriptor(".jar"), textComponentAccessor)
    }

    val isModified: Boolean
        get() =
            typecheckingMode.isSelected != arendSettings.isBackgroundTypechecking ||
            clauseLimitSwitch.isSelected != arendSettings.withClauseLimit ||
            clauseLimit.number != arendSettings.clauseLimit ||
            checkForUpdatesSwitch.isSelected != arendSettings.checkForUpdates ||
            arendJarTextField.text != arendSettings.pathToArendJar

    fun apply() {
        arendSettings.isBackgroundTypechecking = typecheckingMode.isSelected
        arendSettings.withClauseLimit = clauseLimitSwitch.isSelected
        arendSettings.clauseLimit = clauseLimit.number
        arendSettings.checkForUpdates = checkForUpdatesSwitch.isSelected
        arendSettings.pathToArendJar = arendJarTextField.text
    }

    fun reset() {
        typecheckingMode.isSelected = arendSettings.isBackgroundTypechecking
        clauseLimitSwitch.isSelected = arendSettings.withClauseLimit
        clauseLimit.value = arendSettings.clauseLimit
        checkForUpdatesSwitch.isSelected = arendSettings.checkForUpdates
        arendJarTextField.text = arendSettings.pathToArendJar
    }

    fun createComponent() = panel {
        row { cell(typecheckingMode) }
        checked(clauseLimitSwitch, clauseLimit)
        row { cell(checkForUpdatesSwitch) }
        aligned("Path to Arend jar for debugger: ", arendJarTextField)
    }
}