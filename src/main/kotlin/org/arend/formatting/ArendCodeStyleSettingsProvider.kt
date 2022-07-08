package org.arend.formatting

import com.intellij.application.options.*
import com.intellij.lang.Language
import com.intellij.psi.codeStyle.*
import org.arend.ArendLanguage
import org.arend.settings.ArendCustomCodeStyleSettings

class ArendCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {
    override fun getLanguage(): Language = ArendLanguage.INSTANCE

    override fun getCodeSample(settingsType: SettingsType): String = """
        \import Logic
        \module M \where {
        \data Dec (E : \Type)
        | yes E
        | no (Not E)
        \class Decide (E : \Type)
        | decide : Dec E
        \func DecEq (A : \Type) => \Pi {a a' : A} ->
        Dec (a = a')
        }
    """.trimIndent()

    override fun getIndentOptionsEditor(): IndentOptionsEditor = SmartIndentOptionsEditor(this)

    override fun customizeDefaults(commonSettings: CommonCodeStyleSettings, indentOptions: CommonCodeStyleSettings.IndentOptions) {
        commonSettings.indentOptions?.CONTINUATION_INDENT_SIZE = 4
        commonSettings.indentOptions?.INDENT_SIZE = 2
    }

    override fun customizeSettings(consumer: CodeStyleSettingsCustomizable, settingsType: SettingsType) {
        if (settingsType == SettingsType.INDENT_SETTINGS) {
            consumer.showStandardOptions(
                CodeStyleSettingsCustomizable.IndentOption.INDENT_SIZE.name,
                CodeStyleSettingsCustomizable.IndentOption.CONTINUATION_INDENT_SIZE.name,
                CodeStyleSettingsCustomizable.IndentOption.TAB_SIZE.name,
                CodeStyleSettingsCustomizable.IndentOption.USE_TAB_CHARACTER.name,
                CodeStyleSettingsCustomizable.IndentOption.SMART_TABS.name,
                CodeStyleSettingsCustomizable.IndentOption.KEEP_INDENTS_ON_EMPTY_LINES.name)
        }
    }

    override fun createCustomSettings(settings: CodeStyleSettings): CustomCodeStyleSettings {
        return ArendCustomCodeStyleSettings(settings)
    }

    override fun createConfigurable(baseSettings: CodeStyleSettings, modelSettings: CodeStyleSettings) = object : CodeStyleAbstractConfigurable(baseSettings, modelSettings, "Arend") {
        override fun createPanel(settings: CodeStyleSettings) = object : TabbedLanguageCodeStylePanel(ArendLanguage.INSTANCE, currentSettings, settings) {
            override fun initTabs(settings: CodeStyleSettings) {
                addIndentOptionsTab(settings)
                addTab(ArendCodeStyleImportsPanelWrapper(settings))
            }
        }
    }
}