package org.arend.toolWindow.errors

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import org.arend.ArendIcons

class ArendPinErrorAction: ToggleAction("Pin message", "When the message is pinned, other messages will not be displayed", ArendIcons.PIN) {
    override fun isSelected(e: AnActionEvent): Boolean {
        val service = getMessagesService(e)
        return service?.isErrorTextPinned ?: false
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val service = getMessagesService(e)
        service?.isErrorTextPinned = state
        if (!state) service?.setActiveEditor()
    }

    private fun getMessagesService(e: AnActionEvent): ArendMessagesService? =
            e.dataContext.getData(PlatformDataKeys.PROJECT)?.service()
}