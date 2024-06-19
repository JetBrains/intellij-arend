package org.arend.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import org.arend.ArendIcons
import org.arend.settings.ArendProjectSettings

object ArendNormalizeToggleAction : ToggleAction() {
    init {
        templatePresentation.icon = ArendIcons.SHOW_NORMALIZED
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun isSelected(e: AnActionEvent) =
            e.project?.run { service<ArendProjectSettings>().data.popupNormalize } ?: false

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        e.project?.run { service<ArendProjectSettings>().data.popupNormalize = state }
    }
}
