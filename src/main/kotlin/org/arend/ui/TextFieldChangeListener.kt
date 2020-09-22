package org.arend.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentWithBrowseButton.BrowseFolderActionListener
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.VirtualFile
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.JTextField

abstract class TextFieldChangeListener(private val textField: JTextField) {
    protected var previousText: String? = null
        private set

    init {
        textField.addActionListener {
            updateText()
        }
        textField.addFocusListener(object : FocusListener {
            override fun focusLost(e: FocusEvent?) {
                updateText()
            }

            override fun focusGained(e: FocusEvent?) {}
        })
    }

    private fun updateText() {
        textChanged()
        previousText = textField.text
    }

    fun fireEvent() {
        val newText = textField.text
        if (newText != previousText) {
            updateText()
        }
    }

    abstract fun textChanged()
}

fun TextFieldWithBrowseButton.addBrowseAndChangeListener(title: String?, description: String?, project: Project?, fileChooserDescriptor: FileChooserDescriptor, textChanged: (String?) -> Unit): TextFieldChangeListener {
    val browseListener = object : BrowseFolderActionListener<JTextField>(title, description, this, project, fileChooserDescriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT) {
        val changeListener = object : TextFieldChangeListener(textField) {
            override fun textChanged() {
                textChanged(previousText)
            }
        }

        override fun onFileChosen(chosenFile: VirtualFile) {
            super.onFileChosen(chosenFile)
            changeListener.fireEvent()
        }
    }
    addActionListener(browseListener)

    ApplicationManager.getApplication()?.let { if (!it.isUnitTestMode && !it.isHeadlessEnvironment) {
        FileChooserFactory.getInstance().installFileCompletion(textField, fileChooserDescriptor, true, null)
    } }

    return browseListener.changeListener
}