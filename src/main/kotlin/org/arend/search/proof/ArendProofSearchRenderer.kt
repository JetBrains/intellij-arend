package org.arend.search.proof

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.SearchEverywherePsiRenderer
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.psi.PsiElement
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.panels.OpaquePanel
import com.intellij.util.castSafelyTo
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import okhttp3.internal.toHexString
import org.arend.psi.ext.PsiReferable
import org.arend.psi.ext.impl.CoClauseDefAdapter
import org.arend.search.structural.PatternTree
import org.arend.term.abs.Abstract
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import javax.swing.*

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
        if (value is MoreElement) {
            panel.font = UIUtil.getLabelFont().deriveFont(UIUtil.getFontSize(UIUtil.FontSize.SMALL))
            panel.background = JBUI.CurrentTheme.BigPopup.listTitleLabelForeground()
            textArea.contentType = "text/html"
            textArea.text = buildHtmlMore(if (isSelected) list.selectionForeground else UIUtil.getInactiveTextColor())
            panel.border = null
            label.icon = null
        } else {
            value as FoundItemDescriptor<ProofSearchEntry>
            val def = value.item.def.castSafelyTo<Abstract.FunctionDefinition>() ?: return panel
            def as PsiReferable
            val parameterTypes = def.parameters.map { (it as PsiElement).text }
            val type =
                def.castSafelyTo<Abstract.FunctionDefinition>()?.resultType?.castSafelyTo<PsiElement>() ?: return panel
            textArea.contentType = "text/html"
            textArea.text = buildHtml(
                def.name!!,
                parameterTypes,
                type.text,
                if (isSelected) list.selectionForeground else UIUtil.getInactiveTextColor()
            )
            val width = list.width
            if (width > 0) {
                textArea.setSize(width, Short.MAX_VALUE.toInt())
            }
            val icon = if (def is CoClauseDefAdapter) AllIcons.General.Show_to_implement else def.getIcon(0)
            label.icon = icon
            label.border = BorderFactory.createEmptyBorder(
                1 + textArea.getFontMetrics(textArea.font).height - icon.iconHeight,
                0,
                5,
                2
            )
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

    private fun buildHtmlMore(nameColor: Color) : String{
        return """
           <html>
           <body>
           <span style="font-size: small; color: ${toHex(nameColor)}">... more</span>
           </body>
           </html>
        """.trimIndent()
    }

}