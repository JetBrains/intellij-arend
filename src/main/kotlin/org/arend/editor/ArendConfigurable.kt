package org.arend.editor

import com.intellij.openapi.options.SearchableConfigurable
import javax.swing.JComponent


class ArendConfigurable : SearchableConfigurable {
    private var settingsView: ArendSettingsView? = null

    override fun getId() = "preferences.language.Arend"

    override fun getDisplayName() = "Arend"

    override fun isModified() = settingsView?.isModified == true

    override fun apply() {
        settingsView?.apply()
    }

    override fun reset() {
        settingsView?.reset()
    }

    override fun createComponent(): JComponent? {
        settingsView = ArendSettingsView()
        return settingsView?.createComponent()
    }
}