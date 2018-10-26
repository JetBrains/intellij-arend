package org.arend.formatting

import com.intellij.application.options.CodeStyleAbstractConfigurable
import com.intellij.application.options.CodeStyleAbstractPanel
import com.intellij.lang.Language
import com.intellij.openapi.options.Configurable
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider
import com.intellij.psi.codeStyle.CustomCodeStyleSettings
import org.arend.ArendLanguage

class ArendCodeStyleSettingsProvider: CodeStyleSettingsProvider(){
    override fun getConfigurableDisplayName(): String? = "Arend"

    override fun getLanguage(): Language? = ArendLanguage.INSTANCE

    override fun createSettingsPage(settings: CodeStyleSettings?, modelSettings: CodeStyleSettings?): Configurable =
            object : CodeStyleAbstractConfigurable(settings!!, modelSettings, "Arend") {
                override fun createPanel(settings: CodeStyleSettings): CodeStyleAbstractPanel =
                        ArendCodeStyleMainPanel(currentSettings, settings)
            };

    override fun createCustomSettings(settings: CodeStyleSettings?): CustomCodeStyleSettings? =
            ArendCodeStyleSettings(settings)
}