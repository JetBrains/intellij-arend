package org.arend.toolWindow.errors

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import org.arend.ArendIcons
import org.arend.util.ArendBundle

class ArendShowGoalsInErrorsPanelAction : ToggleAction(
        ArendBundle.message("arend.show.goals.in.errors.panel.action.name"),
        ArendBundle.message("arend.show.goals.in.errors.panel.action.description"),
        ArendIcons.GOAL
) {
    override fun isSelected(e: AnActionEvent): Boolean =
            e.project?.service<ArendMessagesService>()?.isShowGoalsInErrorsPanel?.get() ?: true

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        e.project?.service<ArendMessagesService>()?.isShowGoalsInErrorsPanel?.set(state)
    }
}