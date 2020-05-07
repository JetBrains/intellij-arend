package org.arend.ui.impl.session

import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class LabeledComponent(message: String?, override val focused: JComponent) : ComponentSessionItem {
    override val component = if (message == null) focused else {
        val label = JLabel(message)
        label.labelFor = focused

        val panel = JPanel(BorderLayout())
        panel.add(focused)
        panel.add(label, BorderLayout.NORTH)
        panel
    }
}