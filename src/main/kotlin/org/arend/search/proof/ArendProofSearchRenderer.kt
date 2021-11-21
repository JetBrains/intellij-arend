package org.arend.search.proof

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.SearchEverywherePsiRenderer
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.panels.OpaquePanel
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.util.castSafelyTo
import com.intellij.util.ui.UIUtil
import okhttp3.internal.toHexString
import org.arend.psi.ext.PsiReferable
import org.arend.psi.ext.impl.CoClauseDefAdapter
import org.arend.search.structural.PatternTree
import org.arend.term.abs.Abstract
import java.awt.*
import javax.swing.*
import javax.swing.text.html.HTMLDocument
import javax.swing.text.html.HTMLEditorKit

data class ProofSearchEntry(val def : PsiReferable, val tree : PatternTree)

private val BORDER = BorderFactory.createEmptyBorder(3, 0, 5, 0)

class ArendProofSearchRenderer : ListCellRenderer<Any> {
    val panel: JPanel = OpaquePanel(SearchEverywherePsiRenderer.SELayout())
    val iconPanel: JPanel = JPanel(BorderLayout())
    val label: JBLabel = JBLabel()
    val textArea = JEditorPane()

    init {
        iconPanel.add(label, BorderLayout.NORTH)

        panel.add(iconPanel, BorderLayout.WEST)
        panel.add(textArea, BorderLayout.CENTER)
    }

    override fun getListCellRendererComponent(
        list: JList<out Any>,
        value: Any,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        value as FoundItemDescriptor<ProofSearchEntry>
        val def = value.item.def.castSafelyTo<Abstract.FunctionDefinition>() ?: return panel
        def as PsiReferable
        val parameterTypes = def.parameters.map { (it as PsiElement).text }
        val type =
            def.castSafelyTo<Abstract.FunctionDefinition>()?.resultType?.castSafelyTo<PsiElement>() ?: return panel
        textArea.contentType = "text/html"
        textArea.text = buildHtml(def.name!!, parameterTypes, type.text, if (isSelected) list.selectionForeground else UIUtil.getInactiveTextColor())
        val width = list.width
        if (width > 0) {
            textArea.setSize(width, Short.MAX_VALUE.toInt())
        }
        textArea.border = BORDER


        val bgColor = if (isSelected) UIUtil.getListSelectionBackground(true) else UIUtil.getListBackground()
        val textColor = if (isSelected) list.selectionForeground else list.foreground
        textArea.background = bgColor
        panel.background = bgColor
        label.background = bgColor
        iconPanel.background = bgColor
        textArea.foreground = textColor
        textArea.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        textArea.font = JBTextArea().font
        val icon = if (def is CoClauseDefAdapter) AllIcons.General.Show_to_implement else def.getIcon(0)
        label.icon = icon
        label.border = BorderFactory.createEmptyBorder(1 + textArea.getFontMetrics(textArea.font).height - icon.iconHeight, 0, 5, 2)
        return panel

    }

    private fun toHex(color : Color) : String {
        return "#${color.red.toHexString()}${color.blue.toHexString()}${color.green.toHexString()}"
    }

    private fun buildHtml(name : String, parameters : List<String>, type : String, nameColor: Color) : String{
        return """
           <html>
           <body>
           <span style="color: ${toHex(nameColor)}">$name</span> ${parameters.joinToString(" ")} : <b>$type</b>
           </body>
           </html>
        """.trimIndent()
    }

}