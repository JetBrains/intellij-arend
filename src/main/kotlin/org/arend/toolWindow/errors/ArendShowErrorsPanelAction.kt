package org.arend.toolWindow.errors

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import org.arend.util.ArendBundle

class ArendShowErrorsPanelAction : ToggleAction(
        ArendBundle.message("arend.show.errors.panel.action.name"),
        ArendBundle.message("arend.show.errors.panel.action.description"),
        AllIcons.Actions.PreviewDetails
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun isSelected(e: AnActionEvent): Boolean =
            e.project?.service<ArendMessagesService>()?.isShowErrorsPanel?.get() ?: true

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        e.project?.service<ArendMessagesService>()?.isShowErrorsPanel?.set(state)
    }
}
