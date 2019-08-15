package org.arend.editor

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.Label
import com.intellij.ui.layout.panel
import javax.swing.JComponent


class ArendConfigurable : SearchableConfigurable {
    private var typecheckingMode: ComboBox<ArendOptions.TypecheckingMode>? = null
    private var timeLimitSwitch: JBCheckBox? = null
    private var timeLimit: JBIntSpinner? = null

    override fun getId() = "preferences.language.Arend"

    override fun getDisplayName() = "Arend"

    override fun isModified() =
        typecheckingMode?.selectedItem != ArendOptions.instance.typecheckingMode ||
        timeLimitSwitch?.isSelected != ArendOptions.instance.withTimeLimit ||
        timeLimit?.number != ArendOptions.instance.typecheckingTimeLimit

    override fun apply() {
        (typecheckingMode?.selectedItem as? ArendOptions.TypecheckingMode)?.let {
            ArendOptions.instance.typecheckingMode = it
        }
        timeLimitSwitch?.let {
            ArendOptions.instance.withTimeLimit = it.isSelected
        }
        timeLimit?.let {
            ArendOptions.instance.typecheckingTimeLimit = it.number
        }
    }

    override fun reset() {
        typecheckingMode?.selectedItem = ArendOptions.instance.typecheckingMode
        timeLimitSwitch?.isSelected = ArendOptions.instance.withTimeLimit
        timeLimit?.value = ArendOptions.instance.typecheckingTimeLimit

        if (typecheckingMode?.selectedItem != ArendOptions.TypecheckingMode.SMART) {
            timeLimitSwitch?.isEnabled = false
            timeLimit?.isEnabled = false
        } else if (timeLimitSwitch?.isSelected != true) {
            timeLimit?.isEnabled = false
        }
    }

    override fun createComponent(): JComponent? {
        val comboBox = ComboBox(arrayOf(
            ArendOptions.TypecheckingMode.SMART,
            ArendOptions.TypecheckingMode.DUMB,
            ArendOptions.TypecheckingMode.OFF))
        comboBox.renderer = ToolTipListCellRenderer(listOf("Completely typecheck definitions", "Perform only basic checks", "Do not typecheck anything at all"))
        typecheckingMode = comboBox

        val spinner = JBIntSpinner(5, 1, 3600)
        timeLimit = spinner
        val checkBox = JBCheckBox()
        timeLimitSwitch = checkBox
        checkBox.isSelected = true
        checkBox.text = "Stop typechecking after "

        comboBox.addActionListener {
            checkBox.isEnabled = comboBox.selectedItem == ArendOptions.TypecheckingMode.SMART
            spinner.isEnabled = comboBox.selectedItem == ArendOptions.TypecheckingMode.SMART
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