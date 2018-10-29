package org.arend.formatting

import com.intellij.application.options.IndentOptionsEditor
import com.intellij.application.options.SmartIndentOptionsEditor
import com.intellij.lang.Language
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider
import org.arend.ArendLanguage

class ArendLanguageCodeStyleSettingsProvider: LanguageCodeStyleSettingsProvider() {
    override fun getCodeSample(settingsType: SettingsType): String? = "Lol"

    override fun getIndentOptionsEditor(): IndentOptionsEditor? = SmartIndentOptionsEditor(this)

    override fun getDefaultCommonSettings(): CommonCodeStyleSettings? {
        val defaultSettings = CommonCodeStyleSettings(ArendLanguage.INSTANCE)
        defaultSettings.initIndentOptions()
        defaultSettings.indentOptions?.CONTINUATION_INDENT_SIZE = 4
        defaultSettings.indentOptions?.INDENT_SIZE = 2
        return defaultSettings
    }

    override fun getLanguage(): Language = ArendLanguage.INSTANCE
}