package org.arend.ui

import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import org.arend.ArendIcons
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel


class DualList<T : Comparable<T>>(availableText: String, selectedText: String, swapped: Boolean) : JPanel(GridLayoutManager(1, 3)) {
    val availableList = JBList<T>(SimpleListModel())
    val selectedList = JBList<T>(SimpleListModel())

    init {
        val dim = Dimension(-1,-1)
        val availablePane = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JLabel(availableText))
            add(ScrollPaneFactory.createScrollPane(availableList, true).apply { border = IdeBorderFactory.createBorder(SideBorder.ALL) })
        }
        val selectedPane = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JLabel(selectedText))
            add(ToolbarDecorator.createDecorator(selectedList).apply { disableRemoveAction() }.createPanel())
        }

        add(if (swapped) selectedPane else availablePane, GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, dim, dim, dim, 0, false))

        add(JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(IconButton(if (swapped) ArendIcons.MOVE_LEFT else ArendIcons.MOVE_RIGHT).apply { addActionListener {
                moveSelected(availableList, selectedList)
            } })
            add(IconButton(if (swapped) ArendIcons.MOVE_RIGHT else ArendIcons.MOVE_LEFT).apply { addActionListener {
                moveSelected(selectedList, availableList)
                (availableList.model as SimpleListModel).let {
                    it.list = it.list.sorted()
                }
            }})
        }, GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_WANT_GROW, dim, dim, dim, 0, false))

        add(if (swapped) availablePane else selectedPane, GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, dim, dim, dim, 0, false))
    }
}

var <T> JBList<T>.content: List<T>
    get() = (model as SimpleListModel).list
    set(value) {
        (model as SimpleListModel).list = value
    }

private fun <T> moveSelected(from: JBList<T>, to: JBList<T>) {
    val fromModel = from.model
    val removed = ArrayList<T>()
    for (index in from.selectedIndices.reversed()) {
        removed.add(fromModel.getElementAt(index))
        (fromModel as SimpleListModel).remove(index)
    }

    val toModel = to.model
    (toModel as SimpleListModel).addAll(toModel.size, removed)
}