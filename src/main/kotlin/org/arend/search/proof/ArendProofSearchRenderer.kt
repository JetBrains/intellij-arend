package org.arend.search.proof

import com.intellij.ide.actions.SearchEverywherePsiRenderer
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.panels.OpaquePanel
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.util.castSafelyTo
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.arend.psi.ext.PsiReferable
import org.arend.search.structural.PatternTree
import org.arend.term.abs.Abstract
import java.awt.*
import java.awt.font.FontRenderContext
import java.awt.font.LineMetrics
import java.awt.geom.Rectangle2D
import java.text.CharacterIterator
import javax.swing.BorderFactory
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.text.DefaultHighlighter

data class ProofSearchEntry(val def : PsiReferable, val tree : PatternTree)

private val BORDER = BorderFactory.createEmptyBorder(3, 0, 5, 0)

class ArendProofSearchRenderer : ListCellRenderer<Any> {
    val panel: JPanel = OpaquePanel(SearchEverywherePsiRenderer.SELayout())
    val iconPanel: JPanel = JPanel(BorderLayout())
    val label: JBLabel = JBLabel()
    val textArea: JBTextArea = JBTextArea() // todo: html document

    init {
        iconPanel.add(label, BorderLayout.NORTH)

        panel.add(iconPanel, BorderLayout.WEST)
        textArea.lineWrap = true
        textArea.wrapStyleWord = true
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
        val def = value.item.def
        val type =
            def.castSafelyTo<Abstract.FunctionDefinition>()?.resultType?.castSafelyTo<PsiElement>() ?: return panel
        textArea.text = def.name + " : " + type.text
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
        textArea.rows
        val icon = def.getIcon(0)
        label.icon = icon
        label.border = BorderFactory.createEmptyBorder(3 + textArea.getFontMetrics(textArea.font).height - icon.iconHeight, 0, 5, 2)
        return panel
//        removeAll()
//        val leftRenderer: ListCellRenderer<Any> = CustomLeftRenderer()
//        var result: Component
//        SlowOperations.allowSlowOperations(SlowOperations.RENDERING).use { ignore ->
//            result = leftRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
//        }
//        val leftCellRendererComponent = result
//        add(leftCellRendererComponent, BorderLayout.WEST)
//        val bg = if (isSelected) UIUtil.getListSelectionBackground(true) else leftCellRendererComponent.background
//        background = bg
//        return this
    }

    private class CustomLeftRenderer : ColoredListCellRenderer<Any>() {
        override fun customizeCellRenderer(
            list: JList<out Any>,
            preval: Any,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean
        ) {
            preval as FoundItemDescriptor<ProofSearchEntry>
            val value = preval.item!!
            val def = value.def
            val bgColor = UIUtil.getListBackground()
            val color = list.foreground
            val name = def.name
            append("$name : ", SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, color))
            val type = def.castSafelyTo<Abstract.FunctionDefinition>()?.resultType?.castSafelyTo<PsiElement>() ?: return
            val plain = SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, color)
            if (selected) {
                setDynamicSearchMatchHighlighting(true)
                val highlighted = SimpleTextAttributes(
                    bgColor,
                    color,
                    null,
                    SimpleTextAttributes.STYLE_BOLD or SimpleTextAttributes.STYLE_SEARCH_MATCH
                )
                SpeedSearchUtil.appendColoredFragments(
                    this,
                    type.text,
                    listOf(TextRange(0, type.text.length)),
                    plain,
                    highlighted
                )
            } else {
                append(type.text, plain)
            }
            icon = def.getIcon(0)
            background = if (selected) UIUtil.getListSelectionBackground(true) else bgColor
        }

    }

}