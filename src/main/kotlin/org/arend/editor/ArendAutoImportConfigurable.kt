package org.arend.editor

import com.intellij.application.options.editor.AutoImportOptionsProvider
import com.intellij.openapi.components.service
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.panel
import org.arend.settings.ArendSettings
import javax.swing.JComponent

class ArendAutoImportConfigurable : AutoImportOptionsProvider {
    private val arendSettings = service<ArendSettings>()
    private var myOnTheFlyBox: JBCheckBox? = null
    private var myOpenCmdBox: JBCheckBox? = null

    override fun isModified() = myOnTheFlyBox?.isSelected != arendSettings.autoImportOnTheFly ||
            myOpenCmdBox?.isSelected != arendSettings.autoImportWriteOpenCommands

    override fun apply() {
        arendSettings.autoImportOnTheFly = myOnTheFlyBox?.isSelected ?: return
        arendSettings.autoImportWriteOpenCommands = myOpenCmdBox?.isSelected ?: return
    }

    override fun reset() {
        myOnTheFlyBox?.isSelected = arendSettings.autoImportOnTheFly
        myOpenCmdBox?.isSelected = arendSettings.autoImportWriteOpenCommands
    }

    override fun createComponent(): JComponent? {
        val onTheFlyBox = JBCheckBox("Add unambiguous imports on the fly")
        val openCmdBox = JBCheckBox("Prefer \\open commands to long names")
        myOnTheFlyBox = onTheFlyBox
        myOpenCmdBox = openCmdBox

        val panel = panel {
            row { onTheFlyBox() }
            row { openCmdBox() }
        }
        panel.border = IdeBorderFactory.createTitledBorder("Arend")
        return panel
    }
}