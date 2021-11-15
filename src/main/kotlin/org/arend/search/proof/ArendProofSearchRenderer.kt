package org.arend.search.proof

import com.intellij.ide.actions.SearchEverywherePsiRenderer
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.util.SlowOperations
import com.intellij.util.castSafelyTo
import com.intellij.util.ui.UIUtil
import org.arend.psi.ext.PsiReferable
import org.arend.search.structural.PatternTree
import org.arend.term.abs.Abstract
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

data class ProofSearchEntry(val def : PsiReferable, val tree : PatternTree)


class ArendProofSearchRenderer : JPanel(BorderLayout()), ListCellRenderer<Any> {
    init {
        layout = SearchEverywherePsiRenderer.SELayout()
    }

    override fun getListCellRendererComponent(
        list: JList<out Any>,
        value: Any,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        removeAll()
        val leftRenderer: ListCellRenderer<Any> = CustomLeftRenderer()
        var result: Component
        SlowOperations.allowSlowOperations(SlowOperations.RENDERING).use { ignore ->
            result = leftRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        }
        val leftCellRendererComponent = result
        add(leftCellRendererComponent, BorderLayout.WEST)
        val bg = if (isSelected) UIUtil.getListSelectionBackground(true) else leftCellRendererComponent.background
        background = bg
        return this
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