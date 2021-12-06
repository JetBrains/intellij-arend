package org.arend.toolWindow.errors

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.ui.LayeredIcon
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.IconUtil
import org.arend.ArendIcons
import javax.swing.JLabel
import javax.swing.SwingConstants

class ArendAutoClearGoalAction: ToggleAction("Clear Goals Automatically", "Clear goals when they are removed from the source code", ICON) {
    override fun isSelected(e: AnActionEvent): Boolean = e.project?.service<ArendMessagesService>()?.isAutoClearGoals ?: false

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val service = e.project?.service<ArendMessagesService>()
        service?.isAutoClearGoals = state
        if (!state) service?.updateEditor()
    }

    companion object {
        private val ICON by lazy {
            val icon = LayeredIcon(2)
            icon.setIcon(ArendIcons.CLEAR, 0)
            icon.setIcon(IconUtil.textToIcon("AUTO", JLabel(), JBUIScale.scale(6f)), 1, SwingConstants.SOUTH)
            icon
        }
    }
}