package org.arend.ui

import com.intellij.ui.*
import com.intellij.ui.components.JBList
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import org.arend.ArendIcons
import java.awt.Component
import java.awt.Dimension
import javax.swing.*


open class DualList<T : Comparable<T>>(availableText: String, selectedText: String, swapped: Boolean) : JPanel(GridLayoutManager(1, 3)) {
    val listCellRenderer = object : ColoredListCellRenderer<T>() {
        override fun customizeCellRenderer(list: JList<out T>, value: T?, index: Int, selected: Boolean, hasFocus: Boolean) {
            if (value != null) {
                icon = getIcon(value)
                append(value.toString(), if (isAvailable(value)) SimpleTextAttributes.REGULAR_ATTRIBUTES else SimpleTextAttributes.ERROR_ATTRIBUTES, true)
            }
        }
    }
    val availableList = JBList<T>(SimpleListModel()).apply {
        cellRenderer = listCellRenderer
    }
    val selectedList = JBList<T>(SimpleListModel()).apply {
        cellRenderer = listCellRenderer
    }

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

        // This must be invoked after the ToolbarDecorator panel is created since it installs its own drag and drop support
        ListsDnD<T>().apply {
            add(availableList, false) { _, element ->
                availableList.content = (availableList.content + element).sorted()
            }
            add(selectedList, true) { index, element ->
                (selectedList.model as SimpleListModel).add(index, element)
            }
            install()
        }

        add(if (swapped) selectedPane else availablePane, GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, dim, dim, dim, 0, false))

        add(JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(IconButton(if (swapped) ArendIcons.MOVE_LEFT else ArendIcons.MOVE_RIGHT).apply { addActionListener {
                moveSelected(availableList, selectedList)
            } })
            add(IconButton(if (swapped) ArendIcons.MOVE_RIGHT else ArendIcons.MOVE_LEFT).apply { addActionListener {
                moveSelected(selectedList, availableList)
                availableList.content = availableList.content.sorted()
            }})
        }, GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_WANT_GROW, dim, dim, dim, 0, false))

        add(if (swapped) availablePane else selectedPane, GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, dim, dim, dim, 0, false))
    }

    final override fun add(component: Component, constraints: Any?) {
        super.add(component, constraints)
    }

    open fun isAvailable(t : T) = true

    open fun getIcon(t : T): Icon? = null

    private fun moveSelected(from: JBList<T>, to: JBList<T>) {
        val fromModel = from.model
        val removed = ArrayList<T>()
        for (index in from.selectedIndices.reversed()) {
            val element = fromModel.getElementAt(index)
            if (isAvailable(element)) {
                removed.add(element)
            }
            (fromModel as SimpleListModel).remove(index)
        }

        val toModel = to.model
        (toModel as SimpleListModel).addAll(toModel.size, removed)
    }
}

var <T> JBList<T>.content: List<T>
    get() = (model as SimpleListModel).list
    set(value) {
        (model as SimpleListModel).list = value
    }