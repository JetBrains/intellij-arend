package org.arend.ui.impl.session

import org.arend.ext.ui.ArendQuery
import org.arend.ext.ui.ArendSession
import org.arend.extImpl.ui.BaseSession
import org.arend.ui.impl.query.CheckBoxQuery
import org.arend.ui.impl.query.ComboBoxQuery
import org.arend.ui.impl.query.SpinnerQuery
import org.arend.ui.impl.query.TextFieldQuery
import javax.swing.JComponent

abstract class ComponentSession : BaseSession() {
    protected val components = ArrayList<JComponent>()

    override fun binaryQuery(message: String?, defaultValue: Boolean?): ArendQuery<Boolean> {
        val query = CheckBoxQuery(message, defaultValue ?: false)
        components.add(query.checkBox)
        return query
    }

    override fun stringQuery(message: String?, defaultValue: String?): ArendQuery<String> {
        val query = TextFieldQuery(message, defaultValue)
        components.add(query.textField)
        return query
    }

    override fun intQuery(message: String?, defaultValue: Int?): ArendQuery<Int> {
        val query = SpinnerQuery(message, defaultValue ?: 0)
        components.add(query.spinner)
        return query
    }

    protected fun <T> comboBoxQuery(index: Int, message: String?, options: List<T>, defaultOption: T?): ComboBoxQuery<T> {
        val query = ComboBoxQuery(message, options)
        query.comboBox.selectedItem = defaultOption
        components.add(index, query.comboBox)
        return query
    }

    override fun <T> listQuery(message: String?, options: List<T>, defaultOption: T?): ArendQuery<T> =
        comboBoxQuery(components.size, message, options, defaultOption)

    override fun embedded(session: ArendSession) {
        // TODO[ui_api]
    }
}