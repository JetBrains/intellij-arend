package org.arend.toolWindow.errors

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import org.arend.ArendIcons
import org.arend.util.ArendBundle

class ArendPinGoalAction : ToggleAction(
        ArendBundle.message("arend.pin.goal.action.name"),
        ArendBundle.message("arend.pin.goal.action.description"),
        ArendIcons.PIN
) {
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