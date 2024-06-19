package org.arend.toolWindow.errors

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import org.arend.ArendIcons
import org.arend.util.ArendBundle

class ArendPinErrorAction : ToggleAction(
        ArendBundle.message("arend.pin.error.action.name"),
        ArendBundle.message("arend.pin.error.action.description"),
        ArendIcons.PIN
) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun isSelected(e: AnActionEvent): Boolean =
            e.project?.service<ArendMessagesService>()?.isErrorTextPinned ?: false

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val service = e.project?.service<ArendMessagesService>() ?: return
        service.isErrorTextPinned = state
        if (!state) {
            service.updateEditors()
        }
    }

    companion object {
        const val ID = "Arend.PinError"
    }
}
