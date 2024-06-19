package org.arend.toolWindow.errors

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import org.arend.settings.ArendProjectSettings

class ArendMessagesFilterAction(private val project: Project, private val type: MessageType, private val group: ArendMessagesFilterActionGroup)
    : ToggleAction("Show ${type.toText()}s", null, null), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    val isSelected: Boolean
        get() {
            val filterSet = project.service<ArendProjectSettings>().messagesFilterSet
            return filterSet.contains(type) && (!(type == MessageType.RESOLVING || type == MessageType.PARSING) || filterSet.contains(MessageType.SHORT))
        }

    override fun isSelected(e: AnActionEvent) = isSelected

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val filterSet = project.service<ArendProjectSettings>().messagesFilterSet
        if (filterSet.contains(type) == state) {
            return
        }

        if (state) {
            filterSet.add(type)
        } else {
            filterSet.remove(type)
        }
        group.setSelected(type, state)
        project.service<ArendMessagesService>().update()
    }
}
