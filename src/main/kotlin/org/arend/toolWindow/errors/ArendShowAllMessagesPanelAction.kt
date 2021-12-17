package org.arend.toolWindow.errors

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import org.arend.util.ArendBundle

class ArendShowAllMessagesPanelAction : ToggleAction(
        ArendBundle.message("arend.show.all.messages.panel.action.name"),
        ArendBundle.message("arend.show.all.messages.panel.action.description"),
        AllIcons.Actions.PreviewDetails
) {
    override fun isSelected(e: AnActionEvent): Boolean =
            e.project?.service<ArendMessagesService>()?.isShowAllMessagesPanel?.get() ?: true

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        e.project?.service<ArendMessagesService>()?.isShowAllMessagesPanel?.set(state)
    }
}