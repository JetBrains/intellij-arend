package org.arend.ui.impl.session

import com.intellij.CommonBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.JBColor
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.content.Content
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.*
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagLayout
import javax.swing.*

class ArendToolWindowSession(project: Project) : ComponentSession() {
    private val service = project.service<ArendSessionsService>()
    private var tab: Content? = null

    private val okButton = JButton(CommonBundle.getOkButtonText()).apply {
        addActionListener {
            callback?.accept(true)
            tab?.let { service.removeTab(it) }
        }
    }

    private val cancelButton = JButton(CommonBundle.getCancelButtonText()).apply {
        addActionListener {
            tab?.let { service.removeTab(it) }
        }
    }

    // This is copied from DialogWrapper.createSouthPanel
    private fun createSouthPanel(): JPanel {
        val panel = NonOpaquePanel(GridBagLayout())
        val insets = if (SystemInfo.isMac && UIUtil.isUnderIntelliJLaF()) JBInsets.create(0, 8) else JBUI.emptyInsets()
        val bag = GridBag().setDefaultInsets(insets)
        panel.add(Box.createHorizontalGlue(), bag.next().weightx(1.0).fillCellHorizontally()) // left strut
        panel.add(createButtonsPanel(), bag.next())
        return panel
    }

    private fun createButtonsPanel(): JPanel {
        val buttons = listOf(okButton, cancelButton)
        val buttonsPanel: JPanel = NonOpaquePanel()
        buttonsPanel.layout = BoxLayout(buttonsPanel, BoxLayout.X_AXIS)
        for (i in buttons.indices) {
            val button = buttons[i]
            val insets = button.insets
            buttonsPanel.add(button)
            if (i < buttons.size - 1) {
                val gap = if (!JBColor.isBright() || UIUtil.isUnderIntelliJLaF()) JBUIScale.scale(12) - insets.left - insets.right else JBUIScale.scale(8)
                buttonsPanel.add(Box.createRigidArea(Dimension(gap, 0)))
            }
        }
        return buttonsPanel
    }

    override fun doStart() {
        val panel = JPanel(BorderLayout())
        panel.add(component.apply { border = BorderFactory.createEmptyBorder(5, 5, 0, 5) }, BorderLayout.CENTER)
        panel.add(createSouthPanel(), BorderLayout.SOUTH)
        tab = service.addTab(panel, focused, okButton, description, callback)
//        SwingUtilities.getRootPane(okButton)?.defaultButton = okButton
    }
}