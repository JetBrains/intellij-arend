package org.arend.ui.cellRenderer

import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JList


class ToolTipListCellRenderer(private val toolTips: List<String>) : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(list: JList<*>, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
        val comp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        if (index >= 0 && index < toolTips.size) {
            list.toolTipText = toolTips[index]
        }
        return comp
    }
}