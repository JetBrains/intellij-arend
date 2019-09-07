package org.arend.ui

import com.intellij.ide.dnd.DnDDragStartBean
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDSupport
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.util.ui.EditableModel
import java.awt.Rectangle
import javax.swing.JList
import javax.swing.TransferHandler

class ListsDnD<T> {
    private class RowDragInfo(val list: JList<*>, val row: Int)

    private class ListData<T>(val list: JList<T>, val canChoosePosition: Boolean, val dropHandler: (Int, T) -> Unit)

    private val lists = ArrayList<ListData<T>>()

    fun add(list: JList<T>, canChoosePosition: Boolean, dropHandler : (Int, T) -> Unit) {
        assert(list.model is EditableModel)
        list.dragEnabled = true
        list.transferHandler = TransferHandler(null)
        lists.add(ListData(list, canChoosePosition, dropHandler))
    }

    fun install() {
        for (listData in lists) {
            val list = listData.list
            DnDSupport.createBuilder(list)
                .setBeanProvider { DnDDragStartBean(RowDragInfo(list, list.locationToIndex(it.point))) }
                .setTargetChecker { event ->
                    val info = event.attachedObject
                    if (!(info is RowDragInfo && lists.any { it.list == info.list })) {
                        event.setDropPossible(false, "")
                        return@setTargetChecker true
                    }

                    val oldIndex = info.row
                    val newIndex = list.locationToIndex(event.point)

                    val isSameList = list == info.list
                    if (isSameList) {
                        if (oldIndex == newIndex) {
                            return@setTargetChecker true
                        }
                        if (newIndex == -1 || !listData.canChoosePosition || (list.model as? EditableModel)?.canExchangeRows(oldIndex, newIndex) != true) {
                            event.isDropPossible = false
                            return@setTargetChecker true
                        }
                    }

                    event.isDropPossible = true
                    if (newIndex != -1 && listData.canChoosePosition) {
                        val cellBounds = list.getCellBounds(newIndex, newIndex)
                        if (isSameList && oldIndex < newIndex || !isSameList && isBelow(event.point.y, cellBounds)) {
                            cellBounds.y += cellBounds.height - 2
                        }

                        val rectangle = RelativeRectangle(list, cellBounds)
                        rectangle.dimension.height = 2
                        event.setHighlighting(rectangle, DnDEvent.DropTargetHighlightingType.FILLED_RECTANGLE)
                    }

                    true
                }
                .setDropHandler { event ->
                    val info = event.attachedObject as? RowDragInfo
                    val sourceList = if (info == null) null else lists.find { it.list == info.list }?.list
                    if (info != null && sourceList != null) {
                        val oldIndex = info.row
                        if (oldIndex == -1) {
                            return@setDropHandler
                        }
                        val newIndex = list.locationToIndex(event.point).let {
                            if (it == -1) list.model.size - 1 else it
                        }

                        if (list == sourceList) {
                            val model = list.model
                            if (oldIndex != newIndex && newIndex != -1 && (model as? EditableModel)?.canExchangeRows(oldIndex, newIndex) == true) {
                                model.exchangeRows(oldIndex, newIndex)
                                list.selectedIndex = newIndex
                            }
                        } else {
                            val sourceModel = sourceList.model
                            val element = sourceModel.getElementAt(oldIndex)
                            (sourceModel as? EditableModel)?.removeRow(oldIndex)
                            if (element != null) {
                                listData.dropHandler(if (newIndex == -1 || isBelow(event.point.y, list.getCellBounds(newIndex, newIndex))) list.model.size else newIndex, element)
                            }
                        }
                    }
                    event.hideHighlighter()
                }
                .install()
        }
    }

    private fun isBelow(y: Int, rect: Rectangle) = rect.y + rect.height < y
}
