package org.arend.toolWindow.errors

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import org.arend.ArendIcons

class ArendClearGoalAction : AnAction("Clear Goal", "Clear goal", ArendIcons.CLEAR) {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.service<ArendMessagesService>()?.clearGoalText()
    }

    companion object {
        const val ID = "Arend.ClearGoal"
    }
}