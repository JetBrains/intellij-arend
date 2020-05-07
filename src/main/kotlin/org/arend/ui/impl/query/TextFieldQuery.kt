package org.arend.ui.impl.query

import com.intellij.ui.components.JBTextField
import org.arend.ext.ui.ArendQuery

class TextFieldQuery(text: String?) : ArendQuery<String> {
    val textField = JBTextField(text)

    override fun getResult(): String = textField.text
}