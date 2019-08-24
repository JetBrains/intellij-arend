package org.arend.editor

import com.intellij.openapi.components.service
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.Label
import com.intellij.ui.layout.panel
import javax.swing.JPanel


class ArendSmartKeysConfigurable : UnnamedConfigurable {
    private val arendOptions = service<ArendOptions>()
    private var comboBox: ComboBox<ArendOptions.MatchingCommentStyle>? = null

    override fun isModified() = comboBox?.selectedItem != arendOptions.matchingCommentStyle

    override fun apply() {
        arendOptions.matchingCommentStyle = comboBox?.selectedItem as? ArendOptions.MatchingCommentStyle ?: return
    }

    override fun reset() {
        comboBox?.selectedItem = arendOptions.matchingCommentStyle
    }

    override fun createComponent(): JPanel {
        val combo = ComboBox(arrayOf(
            ArendOptions.MatchingCommentStyle.DO_NOTHING,
            ArendOptions.MatchingCommentStyle.REPLACE_BRACE,
            ArendOptions.MatchingCommentStyle.INSERT_MINUS))
        comboBox = combo

        val panel = panel {
            row(Label("On typing '-' between {}: ")) { combo() }
        }
        panel.border = IdeBorderFactory.createTitledBorder("Arend")
        return panel
    }
}