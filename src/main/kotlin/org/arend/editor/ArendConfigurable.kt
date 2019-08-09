package org.arend.editor

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.Label
import com.intellij.ui.layout.panel
import javax.swing.JComponent


class ArendConfigurable : SearchableConfigurable {
    private var comboBox: ComboBox<ArendOptions.TypecheckingMode>? = null

    override fun getId() = "preferences.language.Arend"

    override fun getDisplayName() = "Arend"

    override fun isModified() = comboBox?.selectedItem != ArendOptions.instance.typecheckingMode

    override fun apply() {
        ArendOptions.instance.typecheckingMode = comboBox?.selectedItem as? ArendOptions.TypecheckingMode ?: return
    }

    override fun reset() {
        comboBox?.selectedItem = ArendOptions.instance.typecheckingMode
    }

    override fun createComponent(): JComponent? {
        val combo = ComboBox(arrayOf(
            ArendOptions.TypecheckingMode.SMART,
            ArendOptions.TypecheckingMode.DUMB,
            ArendOptions.TypecheckingMode.OFF))
        combo.renderer = ToolTipListCellRenderer(listOf("Completely typecheck definitions", "Perform only basic checks", "Do not typecheck anything at all"))
        comboBox = combo

        val label = Label("Typechecking mode: ")
        label.toolTipText = "Silent typechecking runs in a background thread"
        val typecheckingPanel = panel {
            row(label) { combo() }
        }
        typecheckingPanel.border = IdeBorderFactory.createTitledBorder("Silent typechecking")

        return panel {
            row { typecheckingPanel() }
        }
    }
}