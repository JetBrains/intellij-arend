package org.arend.editor

import com.intellij.openapi.components.service
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.panel
import org.arend.settings.ArendSettings


class ArendSettingsView {
    private val arendSettings = service<ArendSettings>()

    // Background typechecking
    private val typecheckingMode = ComboBox(arrayOf(
        ArendSettings.TypecheckingMode.SMART,
        ArendSettings.TypecheckingMode.DUMB,
        ArendSettings.TypecheckingMode.OFF))
    private val timeLimitSwitch = JBCheckBox("Stop typechecking after ", true)
    private val timeLimit = JBIntSpinner(5, 1, 3600)

    // Other settings
    private val clauseLimitSwitch = JBCheckBox("Limit the maximum number of clauses generated at once: ", true)
    private val clauseLimit = JBIntSpinner(10, 1, 1000)

    init {
        // Background typechecking
        typecheckingMode.renderer = ToolTipListCellRenderer(listOf("Completely typecheck definitions", "Perform only basic checks", "Do not typecheck anything at all"))
        typecheckingMode.addActionListener {
            updateTypecheckingMode()
        }
        timeLimitSwitch.addActionListener {
            updateTimeLimit()
        }

        // Other settings
        clauseLimitSwitch.addActionListener {
            updateClauseLimit()
        }
    }

    private fun updateTimeLimit() {
        timeLimit.isEnabled = typecheckingMode.selectedItem == ArendSettings.TypecheckingMode.SMART && timeLimitSwitch.isSelected
    }

    private fun updateTypecheckingMode() {
        timeLimitSwitch.isEnabled = typecheckingMode.selectedItem == ArendSettings.TypecheckingMode.SMART
        updateTimeLimit()
    }

    private fun updateClauseLimit() {
        clauseLimit.isEnabled = clauseLimitSwitch.isSelected
    }

    val isModified: Boolean
        get() =
            // Background typechecking
            typecheckingMode.selectedItem != arendSettings.typecheckingMode ||
            timeLimitSwitch.isSelected != arendSettings.withTimeLimit ||
            timeLimit.number != arendSettings.typecheckingTimeLimit ||
            // Other settings
            clauseLimitSwitch.isSelected != arendSettings.withClauseLimit ||
            clauseLimit.number != arendSettings.clauseLimit

    fun apply() {
        // Background typechecking
        (typecheckingMode.selectedItem as? ArendSettings.TypecheckingMode)?.let {
            arendSettings.typecheckingMode = it
        }
        arendSettings.withTimeLimit = timeLimitSwitch.isSelected
        arendSettings.typecheckingTimeLimit = timeLimit.number

        // Other settings
        arendSettings.withClauseLimit = clauseLimitSwitch.isSelected
        arendSettings.clauseLimit = clauseLimit.number
    }

    fun reset() {
        // Background typechecking
        typecheckingMode.selectedItem = arendSettings.typecheckingMode
        timeLimitSwitch.isSelected = arendSettings.withTimeLimit
        timeLimit.value = arendSettings.typecheckingTimeLimit
        updateTypecheckingMode()

        // Other settings
        clauseLimitSwitch.isSelected = arendSettings.withClauseLimit
        clauseLimit.value = arendSettings.clauseLimit
        updateClauseLimit()
    }

    fun createComponent() = panel {
        titledRow("Background typechecking") {
            row { cell {
                label("Typechecking mode: ")
                typecheckingMode()
            } }

            row { cell {
                timeLimitSwitch()
                timeLimit()
                label("second(s)")
            } }
        }

        titledRow("Other settings") {
            row { cell {
                clauseLimitSwitch()
                clauseLimit()
            } }
        }
    }
}