package org.arend.toolWindow.errors

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import org.arend.ArendIcons
import org.arend.util.ArendBundle

class ArendShowImplicitGoalsAction : ToggleAction(
        ArendBundle.message("arend.show.implicit.goals.action.name"),
        ArendBundle.message("arend.show.implicit.goals.action.description"),
        ArendIcons.IMPLICIT_GOAL
) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun isSelected(e: AnActionEvent): Boolean =
            e.project?.service<ArendMessagesService>()?.isShowImplicitGoals?.get() ?: false

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        e.project?.service<ArendMessagesService>()?.isShowImplicitGoals?.set(state)
    }
}
