package org.arend.editor

import com.intellij.application.options.editor.AutoImportOptionsProvider
import com.intellij.openapi.components.service
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.panel
import org.arend.settings.ArendSettings

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

    override fun createComponent() =
        panel {
            row { myOnTheFlyBox = checkBox("Add unambiguous imports on the fly").component }
            row { myOpenCmdBox = checkBox("Prefer \\open commands to long names").component }
        }.apply {
            border = IdeBorderFactory.createTitledBorder("Arend")
        }
}