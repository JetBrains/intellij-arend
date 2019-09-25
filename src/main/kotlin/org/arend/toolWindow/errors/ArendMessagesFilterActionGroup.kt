package org.arend.toolWindow.errors

import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import org.arend.ArendIcons
import org.arend.settings.ArendProjectSettings
import org.arend.toolWindow.errors.tree.ArendErrorTreeAutoScrollFromSource
import java.util.*
import kotlin.collections.HashMap

class ArendMessagesFilterActionGroup(project: Project, private val autoScrollFromSource: ArendErrorTreeAutoScrollFromSource) : DefaultActionGroup("Filter messages", true), DumbAware {
    private var actionMap = HashMap<MessageType, ArendMessagesFilterAction>()

    init {
        templatePresentation.icon = ArendIcons.FILTER
        for (type in MessageType.values()) {
            val action = ArendMessagesFilterAction(project, type, this)
            add(action)
            actionMap[type] = action
        }

        if (!project.service<ArendProjectSettings>().messagesFilterSet.contains(MessageType.SHORT)) {
            actionMap[MessageType.RESOLVING]?.templatePresentation?.isEnabled = false
            actionMap[MessageType.PARSING]?.templatePresentation?.isEnabled = false
        }
    }

    fun setSelected(type: MessageType, enabled: Boolean) {
        val types = EnumSet.of(type)
        if (type == MessageType.SHORT) {
            actionMap[MessageType.RESOLVING]?.templatePresentation?.isEnabled = enabled
            actionMap[MessageType.PARSING]?.templatePresentation?.isEnabled = enabled

            if (!enabled || actionMap[MessageType.RESOLVING]?.isSelected == true) {
                types.add(MessageType.RESOLVING)
            }
            if (!enabled || actionMap[MessageType.PARSING]?.isSelected == true) {
                types.add(MessageType.PARSING)
            }
        }
        autoScrollFromSource.setEnabled(types, enabled)
    }
}