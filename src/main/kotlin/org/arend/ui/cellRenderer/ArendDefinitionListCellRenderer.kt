package org.arend.ui.cellRenderer

import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.util.ui.UIUtil
import org.arend.psi.ext.PsiReferable
import org.arend.psi.ext.parametersText
import org.arend.psi.oneLineText
import org.arend.term.abs.Abstract
import java.awt.Component
import javax.swing.BorderFactory
import javax.swing.DefaultListCellRenderer
import javax.swing.JList
import javax.swing.SwingConstants

object ArendDefinitionListCellRenderer : PsiElementListCellRenderer<PsiReferable>() {
    override fun getContainerText(element: PsiReferable, name: String?) =
        (element as? Abstract.ParametersHolder)?.parametersText

    override fun getIconFlags() = 0

    override fun getElementText(element: PsiReferable) = element.refName

    override fun getRightCellRenderer(value: Any?): DefaultListCellRenderer? {
        val tailText = (value as? PsiReferable)?.psiElementType?.oneLineText ?: return null
        return object : DefaultListCellRenderer() {
            override fun getText() = tailText

            override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
                val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                text = tailText
                border = BorderFactory.createEmptyBorder(0, 0, 0, UIUtil.getListCellHPadding())
                horizontalTextPosition = SwingConstants.LEFT
                horizontalAlignment = SwingConstants.RIGHT
                background = if (isSelected) UIUtil.getListSelectionBackground(true) else UIUtil.getListBackground()
                foreground = if (isSelected) UIUtil.getListSelectionForeground(true) else UIUtil.getInactiveTextColor()
                return component
            }
        }
    }
}