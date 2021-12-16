package org.arend.toolWindow.errors

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.ui.LayeredIcon
import com.intellij.util.IconUtil
import org.arend.util.ArendBundle
import javax.swing.SwingConstants

class ArendShowErrorsPanelAction : ToggleAction(
        ArendBundle.message("arend.show.errors.panel.action.name"),
        ArendBundle.message("arend.show.errors.panel.action.description"),
        ICON
) {
    override fun isSelected(e: AnActionEvent): Boolean =
            e.project?.service<ArendMessagesService>()?.isShowErrorsPanel?.get() ?: true

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        e.project?.service<ArendMessagesService>()?.isShowErrorsPanel?.set(state)
    }

    companion object {
        private val ICON by lazy {
            val icon = LayeredIcon(2)
            icon.setIcon(AllIcons.Actions.PreviewDetails, 0)
            icon.setIcon(IconUtil.scale(AllIcons.General.BalloonError, null, 0.5f), 1, SwingConstants.SOUTH_EAST)
            icon
        }
    }
}