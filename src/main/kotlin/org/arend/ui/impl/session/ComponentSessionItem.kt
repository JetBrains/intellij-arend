package org.arend.ui.impl.session

import javax.swing.JComponent

interface ComponentSessionItem {
    val component: JComponent
    val focused: JComponent?
}