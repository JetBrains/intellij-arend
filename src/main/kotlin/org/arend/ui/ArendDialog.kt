package org.arend.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ScrollPaneFactory
import javax.swing.JComponent
import javax.swing.JLabel

class ArendDialog(project: Project, title: String?, private val description: String?, private val component: JComponent?, private val focused: JComponent? = component) : DialogWrapper(project, true) {
    init {
        if (title != null) {
            setTitle(title)
        }
        init()
    }

    override fun createNorthPanel() = description?.let { JLabel(it) }

    override fun createCenterPanel() = component?.let { ScrollPaneFactory.createScrollPane(it, true) }

    override fun getPreferredFocusedComponent() = focused
}