package org.arend.formatting

import com.intellij.application.options.TabbedLanguageCodeStylePanel
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider
import org.arend.ArendLanguage

class ArendCodeStyleMainPanel(currentSettings: CodeStyleSettings, settings: CodeStyleSettings):
        TabbedLanguageCodeStylePanel(ArendLanguage.INSTANCE, currentSettings, settings) {

    override fun initTabs(settings: CodeStyleSettings?) {
        addIndentOptionsTab(settings)

        for (provider in CodeStyleSettingsProvider.EXTENSION_POINT_NAME.extensionList) {
            if (provider.language === ArendLanguage.INSTANCE && !provider.hasSettingsPage()) {
                createTab(provider)
            }
        }

    }
}