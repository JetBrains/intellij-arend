package org.arend.editor

import com.intellij.application.options.editor.AutoImportOptionsProvider
import com.intellij.openapi.components.service
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.panel
import javax.swing.JComponent

class ArendAutoImportConfigurable : AutoImportOptionsProvider {
    private val arendOptions = service<ArendOptions>()
    private var checkBox: JBCheckBox? = null

    override fun isModified() = checkBox?.isSelected != arendOptions.autoImportOnTheFly

    override fun apply() {
        arendOptions.autoImportOnTheFly = checkBox?.isSelected ?: return
    }

    override fun reset() {
        checkBox?.isSelected = arendOptions.autoImportOnTheFly
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