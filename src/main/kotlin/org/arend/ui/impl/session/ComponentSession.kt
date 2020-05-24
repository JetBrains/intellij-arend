package org.arend.ui.impl.session

import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.UIUtil
import org.arend.ext.ui.ArendQuery
import org.arend.ext.ui.ArendSession
import org.arend.extImpl.ui.BaseSession
import org.arend.ui.impl.query.CheckBoxQuery
import org.arend.ui.impl.query.ComboBoxQuery
import org.arend.ui.impl.query.SpinnerQuery
import org.arend.ui.impl.query.TextFieldQuery
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

abstract class ComponentSession : BaseSession(), ComponentSessionItem {
    protected val items = ArrayList<ComponentSessionItem>()
    private var withSeparator = false
    private val embedded = ArrayList<ArendSession>()

    open val allItems: List<ComponentSessionItem>
        get() = items

    override val component: JComponent
        get() {
            if (items.size == 1 && description == null && !withSeparator) {
                return items[0].component
            }

            val panel = JPanel(VerticalLayout(JBUIScale.scale(UIUtil.DEFAULT_VGAP)))
            if (withSeparator) {
                panel.add(TitledSeparator(description, focused))
            }

            var first = true
            for (item in allItems) {
                (item as? ComponentSession)?.let {
                    it.withSeparator = !first || it.description != null
                }
                first = false
                panel.add(item.component)
            }
            return panel
        }

    override val focused: JComponent?
        get() = items.firstOrNull()?.focused

    override fun message(message: String) {
        items.add(LabeledComponent(null, JLabel(message)))
    }

    override fun binaryQuery(message: String?, defaultValue: Boolean?): ArendQuery<Boolean> {
        val query = CheckBoxQuery(message, defaultValue ?: false)
        items.add(LabeledComponent(null, query.checkBox))
        return query
    }

    override fun stringQuery(message: String?, defaultValue: String?): ArendQuery<String> {
        val query = TextFieldQuery(defaultValue)
        items.add(LabeledComponent(message, query.textField))
        return query
    }

    override fun intQuery(message: String?, defaultValue: Int?): ArendQuery<Int> {
        val query = SpinnerQuery(defaultValue ?: 0)
        items.add(LabeledComponent(message, query.spinner))
        return query
    }

    protected fun <T : Any> comboBoxQuery(index: Int, message: String?, options: List<T>, defaultOption: T?): ComboBoxQuery<T> {
        val query = ComboBoxQuery(options)
        query.comboBox.selectedItem = defaultOption
        items.add(index, LabeledComponent(message, query.comboBox))
        return query
    }

    override fun <T : Any> listQuery(message: String?, options: List<T>, defaultOption: T?): ArendQuery<T> =
        comboBoxQuery(items.size, message, options, defaultOption)

    override fun embedded(session: ArendSession) {
        embedded.add(session)
        if (session.javaClass == javaClass) {
            session as ComponentSession
            session.checkAndDisable()
            items.add(session)
        }
    }

    protected abstract fun doStart()

    final override fun startSession() {
        checkAndDisable()

        for (session in embedded) {
            if (session.javaClass != javaClass) {
                session.startSession()
            }
        }

        doStart()
    }

    protected fun endSession(ok: Boolean) {
        for (session in embedded) {
            if (session.javaClass == javaClass) {
                (session as ComponentSession).callback?.accept(ok)
            }
        }
        callback?.accept(ok)
    }
}