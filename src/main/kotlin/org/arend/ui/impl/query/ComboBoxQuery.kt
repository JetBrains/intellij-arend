package org.arend.ui.impl.query

import com.intellij.openapi.ui.ComboBox
import org.arend.ext.ui.ArendQuery

class ComboBoxQuery<T : Any>(list: List<T>) : ArendQuery<T> {
    val comboBox = ComboBox(Array<Any>(list.size) { list[it] })

    @Suppress("UNCHECKED_CAST")
    override fun getResult() = comboBox.selectedItem as? T
}