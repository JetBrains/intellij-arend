package org.arend.ui.impl

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ColoredListCellRenderer
import org.arend.ArendIcons
import org.arend.core.definition.Definition
import org.arend.ext.ui.ArendUI
import org.arend.extImpl.Disableable
import java.util.function.Consumer
import javax.swing.JList
import javax.swing.ListSelectionModel

class ArendUIImpl(private val editor: Editor) : Disableable(), ArendUI {
    private val singleQueryDisabler = Disableable()

    override fun <T> singleQuery(title: String?, message: String?, options: List<T>, callback: Consumer<T>) {
        checkEnabled()
        singleQueryDisabler.checkAndDisable("singleQuery was already invoked")

        val builder = JBPopupFactory.getInstance().createPopupChooserBuilder(options)
            .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            .setFont(EditorUtil.getEditorFont())
            .setRenderer(object : ColoredListCellRenderer<T>() {
                override fun customizeCellRenderer(list: JList<out T>, value: T, index: Int, selected: Boolean, hasFocus: Boolean) {
                    if (value is Definition) {
                        icon = ArendIcons.definitionToIcon(value)
                    }
                    append(value.toString())
                }
            })
            .setItemChosenCallback {
                checkAndDisable()
                callback.accept(it)
            }
        if (title != null) builder.setTitle(title)
        if (message != null) builder.setAdText(message)
        builder.createPopup().showInBestPositionFor(editor)
    }
}