package org.arend.ui.impl.query

import com.intellij.ui.components.JBCheckBox
import org.arend.ext.ui.ArendQuery

class CheckBoxQuery(text: String?, selected: Boolean) : ArendQuery<Boolean> {
    val checkBox = JBCheckBox(text, selected)

    override fun getResult() = checkBox.isSelected
}