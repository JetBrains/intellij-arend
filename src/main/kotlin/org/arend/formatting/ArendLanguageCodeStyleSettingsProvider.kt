package org.arend.formatting

import com.intellij.application.options.IndentOptionsEditor
import com.intellij.application.options.SmartIndentOptionsEditor
import com.intellij.lang.Language
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider
import org.arend.ArendLanguage

class ArendLanguageCodeStyleSettingsProvider: LanguageCodeStyleSettingsProvider() {
    override fun getCodeSample(settingsType: SettingsType): String? = """
        \import Logic
        \data Dec (E : \Type)
        | yes E
        | no (Not E)
        \class Decide (E : \Type)
        | decide : Dec E
        \func DecEq (A : \Type) => \Pi {a a' : A} ->
        Dec (a = a')
    """.trimIndent()

    override fun getIndentOptionsEditor(): IndentOptionsEditor? = SmartIndentOptionsEditor(this)

    override fun customizeDefaults(commonSettings: CommonCodeStyleSettings, indentOptions: CommonCodeStyleSettings.IndentOptions) {
        commonSettings.indentOptions?.CONTINUATION_INDENT_SIZE = 4
        commonSettings.indentOptions?.INDENT_SIZE = 2
    }

    override fun getLanguage(): Language = ArendLanguage.INSTANCE
}