package org.arend.editor

import com.intellij.application.options.editor.AutoImportOptionsProvider
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.panel
import javax.swing.JComponent

class ArendAutoImportConfigurable : AutoImportOptionsProvider {
    private var checkBox: JBCheckBox? = null

    override fun isModified() = checkBox?.isSelected != ArendOptions.getInstance().autoImportOnTheFly

    override fun apply() {
        ArendOptions.getInstance().autoImportOnTheFly = checkBox?.isSelected ?: return
    }

    override fun reset() {
        checkBox?.isSelected = ArendOptions.getInstance().autoImportOnTheFly
    }

    override fun createComponent(): JComponent? {
        val box = JBCheckBox("Add unambiguous imports on the fly")
        checkBox = box

        val panel = panel {
            row { box() }
        }
        panel.border = IdeBorderFactory.createTitledBorder("Arend")
        return panel
    }
}