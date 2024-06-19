package org.arend.toolWindow.errors

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.ui.LayeredIcon
import org.arend.util.ArendBundle

class ArendShowGoalsInErrorsPanelAction : ToggleAction(
        ArendBundle.message("arend.show.goals.in.errors.panel.action.name"),
        ArendBundle.message("arend.show.goals.in.errors.panel.action.description"),
        ICON
) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun isSelected(e: AnActionEvent): Boolean =
            e.project?.service<ArendMessagesService>()?.isShowGoalsInErrorsPanel?.get() ?: true

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        e.project?.service<ArendMessagesService>()?.isShowGoalsInErrorsPanel?.set(state)
    }

    companion object {
        private val ICON = LayeredIcon(2).apply {
            setIcon(AllIcons.Actions.PrettyPrint, 0)
            setIcon(AllIcons.Debugger.Question_badge, 1, 4, 3)
        }
    }
}