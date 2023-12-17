package org.arend.util

import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.layout.selected
import javax.swing.AbstractButton
import javax.swing.JComponent

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

inline fun <T, R> Iterable<T>.mapToSet(transform: (T) -> R) : Set<R> = mapTo(HashSet(), transform)

inline fun <K, T : K, R> Iterable<T>.associateWithWellTyped(selector : (T) -> R) : Map<K, R> = associate { it to selector(it) }

fun <T, U> caching(f : (T) -> U) : (T) -> U {
    val cache = mutableMapOf<T, U>()
    return { cache.computeIfAbsent(it, f) }
}

// UI DSL
fun Panel.labeled(text: String, component: JComponent) = row {
    cell(component).label(text)
}

fun <T : JComponent> Panel.aligned(text: String, component: T, init: Cell<T>.() -> Unit = {}) = row(text) {
    cell(component).align(AlignX.FILL).init()
}

fun <T : JComponent> Panel.checked(checkBox: AbstractButton, component: T, init: Cell<T>.() -> Unit = {}) = row {
    cell(checkBox)
    cell(component).enabledIf(checkBox.selected).init()
}
