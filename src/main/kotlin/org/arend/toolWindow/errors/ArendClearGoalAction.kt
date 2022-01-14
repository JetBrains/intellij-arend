package org.arend.toolWindow.errors

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import org.arend.ArendIcons
import org.arend.util.ArendBundle

class ArendClearGoalAction : AnAction(
        ArendBundle.message("arend.clear.goal.action.name"),
        ArendBundle.message("arend.clear.goal.action.description"),
        ArendIcons.CLEAR
) {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.service<ArendMessagesService>()?.clearGoalEditor()
    }

    companion object {
        const val ID = "Arend.ClearGoal"
    }
}