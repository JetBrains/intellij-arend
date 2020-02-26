package org.arend.util

import com.intellij.ui.layout.Cell
import com.intellij.ui.layout.LayoutBuilder
import com.intellij.ui.layout.Row
import com.intellij.ui.layout.selected
import org.arend.core.context.param.SingleDependentLink
import javax.swing.AbstractButton
import javax.swing.JComponent

fun SingleDependentLink.asSequence() = generateSequence(this) {
    if (it.hasNext()) it.next
    else null
}

inline fun <T, R> Iterable<T>.mapFirstNotNull(transform: (T) -> R?): R? {
    for (item in this) {
        val r = transform(item)
        if (r != null) {
            return r
        }
    }
    return null
}

inline fun <T, R : Any> Iterable<T>.mapUntilNotNull(transform: (T) -> R?): ArrayList<R> {
    val result = ArrayList<R>()
    for (item in this) {
        result.add(transform(item) ?: return result)
    }
    return result
}

// UI DSL

inline fun LayoutBuilder.cellRow(crossinline init: Cell.() -> Unit) {
    row { cell(false, init = init) }
}

inline fun Row.cellRow(crossinline init: Cell.() -> Unit) {
    row { cell(false, init = init) }
}

inline fun LayoutBuilder.labeledRow(text: String, crossinline init: () -> Unit) {
    cellRow {
        label(text)
        init()
    }
}

inline fun Row.labeledRow(text: String, crossinline init: () -> Unit) {
    cellRow {
        label(text)
        init()
    }
}

fun LayoutBuilder.labeled(text: String, component: JComponent) {
    cellRow {
        label(text)
        component()
    }
}

fun Row.labeled(text: String, component: JComponent) {
    cellRow {
        label(text)
        component()
    }
}

fun LayoutBuilder.checked(checkBox: AbstractButton, component: JComponent) {
    cellRow {
        checkBox()
        component().enableIf(checkBox.selected)
    }
}


fun Row.checked(checkBox: AbstractButton, component: JComponent) {
    cellRow {
        checkBox()
        component().enableIf(checkBox.selected)
    }
}
