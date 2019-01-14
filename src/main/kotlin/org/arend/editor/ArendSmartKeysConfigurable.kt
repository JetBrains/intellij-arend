package org.arend.editor

import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.layout.panel
import javax.swing.JPanel


class ArendSmartKeysConfigurable : UnnamedConfigurable {
    private var comboBox: ComboBox<ArendSmartKeysOptions.MatchingCommentStyle>? = null

    override fun isModified() = comboBox?.selectedItem != ArendSmartKeysOptions.getInstance().matchingCommentStyle

    override fun apply() {
        ArendSmartKeysOptions.getInstance().matchingCommentStyle = comboBox?.selectedItem as? ArendSmartKeysOptions.MatchingCommentStyle ?: return
    }

    override fun reset() {
        comboBox?.selectedItem = ArendSmartKeysOptions.getInstance().matchingCommentStyle
    }

    override fun createComponent(): JPanel {
        val combo = ComboBox(arrayOf(
            ArendSmartKeysOptions.MatchingCommentStyle.DO_NOTHING,
            ArendSmartKeysOptions.MatchingCommentStyle.REPLACE_BRACE,
            ArendSmartKeysOptions.MatchingCommentStyle.INSERT_MINUS))
        comboBox = combo

        val panel = panel {
            row("On typing '-' between {}: ") { combo() }
        }
        panel.border = IdeBorderFactory.createTitledBorder("Arend")
        return panel
    }
}