package org.arend.search.proof

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.SearchEverywherePsiRenderer
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.panels.OpaquePanel
import com.intellij.util.ui.UIUtil
import okhttp3.internal.toHexString
import org.arend.psi.ext.PsiReferable
import org.arend.psi.ext.impl.CoClauseDefAdapter
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Font
import javax.swing.*

class ArendSignatureSearchRenderer : ListCellRenderer<SignatureSearchUIEntry> {
    private val panel: JPanel = OpaquePanel(SearchEverywherePsiRenderer.SELayout())
    private val iconPanel: JPanel = JPanel(BorderLayout())
    private val label: JBLabel = JBLabel()
    private val textArea = JEditorPane()

    private val document = DocumentImpl("", true)

    private val editor: EditorEx = EditorFactory.getInstance().createViewer(document) as EditorEx

    init {
        textArea.contentType = "text/html"
        textArea.border = BORDER
        textArea.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        textArea.font = JBTextArea().font
        iconPanel.add(label, BorderLayout.NORTH)
        panel.add(iconPanel, BorderLayout.WEST)
        panel.add(textArea, BorderLayout.CENTER)
        with(editor.settings) {
            isRightMarginShown = false
            isLineNumbersShown = false
            isLineMarkerAreaShown = false
            isRefrainFromScrolling = true
            isCaretRowShown = false
            isUseSoftWraps = true
            setGutterIconsShown(false)
            additionalLinesCount = 0
            additionalColumnsCount = 1
            isFoldingOutlineShown = false
            isVirtualSpace = false
        }
        editor.headerComponent = null
        editor.setHorizontalScrollbarVisible(false)
        editor.setVerticalScrollbarVisible(false)
        editor.isRendererMode = true
    }

    override fun getListCellRendererComponent(
        list: JList<out SignatureSearchUIEntry>,
        value: SignatureSearchUIEntry,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        val bgColor = if (isSelected) UIUtil.getListSelectionBackground(true) else UIUtil.getListBackground()
        val fgColor = if (isSelected) list.selectionForeground else list.foreground
        return when (value) {
            is DefElement -> {
                val (def, codomain) = value.entry
                val text = def.name + " : " + codomain.toString()
                editor.markupModel.removeAllHighlighters()
                document.setText(text)
                editor.backgroundColor = bgColor
                val attrs = TextAttributes(fgColor, null, null, null, Font.PLAIN)
                editor.markupModel.addRangeHighlighter(
                    0,
                    text.length,
                    HighlighterLayer.SELECTION,
                    attrs,
                    HighlighterTargetArea.EXACT_RANGE
                )
                editor.component
            }
            is MoreElement -> {
                textArea.background = bgColor
                textArea.foreground = fgColor
                panel.font = UIUtil.getLabelFont().deriveFont(UIUtil.getFontSize(UIUtil.FontSize.SMALL))
                textArea.text = buildMoreHTML(fgColor)
                panel.border = null
                label.icon = null
                panel
            }
        }
    }
}

private val BORDER = BorderFactory.createEmptyBorder(3, 0, 5, 0)

private fun Color.asHex(): String {
    return "#${red.toHexString()}${blue.toHexString()}${green.toHexString()}"
}

private fun buildMoreHTML(nameColor: Color): String = """
       <html>
       <body>
       <span style="font-size: small; color: ${nameColor.asHex()}">... more</span>
       </body>
       </html>
    """.trimIndent()

private fun getIcon(def: PsiReferable): Icon = when (def) {
    is CoClauseDefAdapter -> AllIcons.General.Show_to_implement
    else -> def.getIcon(0)
}