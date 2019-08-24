package org.arend.editor

import com.intellij.openapi.components.service
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.Label
import com.intellij.ui.layout.panel
import org.arend.settings.ArendSettings
import javax.swing.JComponent


class ArendConfigurable : SearchableConfigurable {
    private val arendOption = service<ArendSettings>()
    private var typecheckingMode: ComboBox<ArendSettings.TypecheckingMode>? = null
    private var timeLimitSwitch: JBCheckBox? = null
    private var timeLimit: JBIntSpinner? = null

    override fun getId() = "preferences.language.Arend"

    override fun getDisplayName() = "Arend"

    override fun isModified() =
        typecheckingMode?.selectedItem != arendOption.typecheckingMode ||
        timeLimitSwitch?.isSelected != arendOption.withTimeLimit ||
        timeLimit?.number != arendOption.typecheckingTimeLimit

    override fun apply() {
        (typecheckingMode?.selectedItem as? ArendSettings.TypecheckingMode)?.let {
            arendOption.typecheckingMode = it
        }
        timeLimitSwitch?.let {
            arendOption.withTimeLimit = it.isSelected
        }
        timeLimit?.let {
            arendOption.typecheckingTimeLimit = it.number
        }
    }

    override fun reset() {
        typecheckingMode?.selectedItem = arendOption.typecheckingMode
        timeLimitSwitch?.isSelected = arendOption.withTimeLimit
        timeLimit?.value = arendOption.typecheckingTimeLimit

        if (typecheckingMode?.selectedItem != ArendSettings.TypecheckingMode.SMART) {
            timeLimitSwitch?.isEnabled = false
            timeLimit?.isEnabled = false
        } else if (timeLimitSwitch?.isSelected != true) {
            timeLimit?.isEnabled = false
        }
    }

    override fun createComponent(): JComponent? {
        val comboBox = ComboBox(arrayOf(
            ArendSettings.TypecheckingMode.SMART,
            ArendSettings.TypecheckingMode.DUMB,
            ArendSettings.TypecheckingMode.OFF))
        comboBox.renderer = ToolTipListCellRenderer(listOf("Completely typecheck definitions", "Perform only basic checks", "Do not typecheck anything at all"))
        typecheckingMode = comboBox

        val spinner = JBIntSpinner(5, 1, 3600)
        timeLimit = spinner
        val checkBox = JBCheckBox()
        timeLimitSwitch = checkBox
        checkBox.isSelected = true
        checkBox.text = "Stop typechecking after "

        comboBox.addActionListener {
            checkBox.isEnabled = comboBox.selectedItem == ArendSettings.TypecheckingMode.SMART
            spinner.isEnabled = comboBox.selectedItem == ArendSettings.TypecheckingMode.SMART
        }
        checkBox.addActionListener {
            spinner.isEnabled = checkBox.isSelected
        }

        return panel {
            titledRow("Silent typechecking") {
                row { cell {
                    val label = Label("Typechecking mode: ")
                    label.toolTipText = "Silent typechecking runs in a background thread"
                    label()
                    comboBox()
                } }

                row { cell {
                    checkBox()
                    spinner()
                    label("second(s)")
                } }
            }
        }
    }
}