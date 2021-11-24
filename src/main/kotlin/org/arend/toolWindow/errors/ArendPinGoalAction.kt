package org.arend.toolWindow.errors

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import org.arend.ArendIcons

class ArendPinGoalAction: ToggleAction("Pin Goal", "When the goal is pinned, other goals will not be displayed", ArendIcons.PIN) {
    override fun isSelected(e: AnActionEvent): Boolean {
        val service = getMessagesService(e)
        return service?.isGoalTextPinned ?: false
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val service = getMessagesService(e)
        service?.isGoalTextPinned = state
        if (!state) service?.updateEditor()
    }

    private fun getMessagesService(e: AnActionEvent): ArendMessagesService? =
            e.dataContext.getData(PlatformDataKeys.PROJECT)?.service()

    companion object {
        const val ID = "Arend.PinGoal"
    }
}