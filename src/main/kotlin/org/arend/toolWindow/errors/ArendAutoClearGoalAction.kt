package org.arend.toolWindow.errors

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.ui.LayeredIcon
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.IconUtil
import org.arend.ArendIcons
import org.arend.util.ArendBundle
import javax.swing.JLabel
import javax.swing.SwingConstants

class ArendAutoClearGoalAction : ToggleAction(
        ArendBundle.message("arend.auto.clear.goal.action.name"),
        ArendBundle.message("arend.auto.clear.goal.action.description"),
        ICON
) {
    override fun isSelected(e: AnActionEvent): Boolean =
            e.project?.service<ArendMessagesService>()?.isAutoClearGoals?.get() ?: false

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        e.project?.service<ArendMessagesService>()?.isAutoClearGoals?.set(state)
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