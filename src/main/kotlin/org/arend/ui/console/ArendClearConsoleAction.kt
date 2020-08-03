package org.arend.ui.console

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.arend.ArendIcons
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.KeyStroke

class ArendClearConsoleAction(private val project: Project, console: JComponent) : AnAction("Clear console", "Clear the contents of the Arend Console", ArendIcons.CLEAR) {
    init {
        registerCustomShortcutSet(CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK)), console)
    }

    override fun actionPerformed(e: AnActionEvent) {
        project.service<ArendConsoleService>().clearText()
    }
}