package org.arend.ui

import com.intellij.ui.CollectionListModel


class SimpleListModel<T> : CollectionListModel<T>() {
    var list: List<T>
        get() = internalList
        set(value) {
            removeAll()
            addAll(0, value)
        }
}