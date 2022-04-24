package org.arend.editor

import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.and
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected
import com.intellij.ui.layout.selectedValueIs
import org.arend.settings.ArendSettings
import org.arend.ui.cellRenderer.ToolTipListCellRenderer
import org.arend.util.aligned
import org.arend.util.labeled
import org.arend.util.checked


class ArendSettingsView {
    private val arendSettings = service<ArendSettings>()

    // Background typechecking
    private val typecheckingMode = ComboBox(arrayOf(
        ArendSettings.TypecheckingMode.SMART,
        ArendSettings.TypecheckingMode.DUMB,
        ArendSettings.TypecheckingMode.OFF))
        .apply {
            renderer = ToolTipListCellRenderer(listOf("Completely typecheck definitions", "Perform only basic checks", "Do not typecheck anything at all"))
        }
    private val timeLimitSwitch = JBCheckBox("Stop typechecking after ", true)
    private val timeLimit = JBIntSpinner(5, 1, 3600)
    private val typecheckOnlyLastSwitch = JBCheckBox("Stop typechecking if the last modified definition has errors", true)

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
            // Background typechecking
            typecheckingMode.selectedItem != arendSettings.typecheckingMode ||
            timeLimitSwitch.isSelected != arendSettings.withTimeLimit ||
            timeLimit.number != arendSettings.typecheckingTimeLimit ||
            typecheckOnlyLastSwitch.isSelected != arendSettings.typecheckOnlyLast ||
            // Other settings
            clauseLimitSwitch.isSelected != arendSettings.withClauseLimit ||
            clauseLimit.number != arendSettings.clauseLimit ||
            checkForUpdatesSwitch.isSelected != arendSettings.checkForUpdates ||
            arendJarTextField.text != arendSettings.pathToArendJar

    fun apply() {
        // Background typechecking
        (typecheckingMode.selectedItem as? ArendSettings.TypecheckingMode)?.let {
            arendSettings.typecheckingMode = it
        }
        arendSettings.withTimeLimit = timeLimitSwitch.isSelected
        arendSettings.typecheckingTimeLimit = timeLimit.number
        arendSettings.typecheckOnlyLast = typecheckOnlyLastSwitch.isSelected

        // Other settings
        arendSettings.withClauseLimit = clauseLimitSwitch.isSelected
        arendSettings.clauseLimit = clauseLimit.number
        arendSettings.checkForUpdates = checkForUpdatesSwitch.isSelected
        arendSettings.pathToArendJar = arendJarTextField.text
    }

    fun reset() {
        // Background typechecking
        typecheckingMode.selectedItem = arendSettings.typecheckingMode
        timeLimitSwitch.isSelected = arendSettings.withTimeLimit
        timeLimit.value = arendSettings.typecheckingTimeLimit
        typecheckOnlyLastSwitch.isSelected = arendSettings.typecheckOnlyLast

        // Other settings
        clauseLimitSwitch.isSelected = arendSettings.withClauseLimit
        clauseLimit.value = arendSettings.clauseLimit
        checkForUpdatesSwitch.isSelected = arendSettings.checkForUpdates
        arendJarTextField.text = arendSettings.pathToArendJar
    }

    fun createComponent() = panel {
        group("Background Typechecking") {
            labeled("Typechecking mode: ", typecheckingMode)
            row {
                cell(timeLimitSwitch).enabledIf(typecheckingMode.selectedValueIs(ArendSettings.TypecheckingMode.SMART))
                cell(timeLimit).enabledIf(typecheckingMode.selectedValueIs(ArendSettings.TypecheckingMode.SMART) and timeLimitSwitch.selected)
                label("second(s)")
            }
            row { cell(typecheckOnlyLastSwitch).enabledIf(typecheckingMode.selectedValueIs(ArendSettings.TypecheckingMode.SMART)) }
        }

        group("Other Settings") {
            checked(clauseLimitSwitch, clauseLimit)
            row { cell(checkForUpdatesSwitch) }
            aligned("Path to Arend jar for debugger: ", arendJarTextField)
        }
    }
}