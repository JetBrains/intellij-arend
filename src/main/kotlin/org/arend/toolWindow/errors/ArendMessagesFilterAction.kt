package org.arend.toolWindow.errors

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import org.arend.ArendIcons
import org.arend.error.GeneralError
import org.arend.settings.ArendProjectSettings
import org.arend.toolWindow.errors.tree.ArendErrorTreeAutoScrollFromSource

class ArendMessagesFilterAction(private val project: Project, private val level: GeneralError.Level, private val autoScrollFromSource: ArendErrorTreeAutoScrollFromSource)
    : ToggleAction("Show ${level.toString().toLowerCase()}s", null, ArendIcons.getErrorLevelIcon(level)), DumbAware {

    override fun isSelected(e: AnActionEvent) =
        project.service<ArendProjectSettings>().messagesFilterSet.contains(level)

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val filterSet = project.service<ArendProjectSettings>().messagesFilterSet
        if (filterSet.contains(level) == state) {
            return
        }

        autoScrollFromSource.setEnabled(level, state)
        if (state) {
            filterSet.add(level)
        } else {
            filterSet.remove(level)
        }
        project.service<ArendMessagesService>().update()
    }
}