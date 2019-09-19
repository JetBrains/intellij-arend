package org.arend.toolWindow.errors

import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import org.arend.ArendIcons
import org.arend.settings.ArendProjectSettings
import org.arend.toolWindow.errors.tree.ArendErrorTreeAutoScrollFromSource

class ArendMessagesFilterActionGroup(project: Project, autoScrollFromSource: ArendErrorTreeAutoScrollFromSource) : DefaultActionGroup("Filter messages", true), DumbAware {
    init {
        templatePresentation.icon = ArendIcons.FILTER
        for (level in ArendProjectSettings.levels) {
            add(ArendMessagesFilterAction(project, level, autoScrollFromSource))
        }
    }
}