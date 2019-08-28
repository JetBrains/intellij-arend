package org.arend.toolWindow.errors

import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import org.arend.ArendIcons
import org.arend.error.GeneralError

class ArendMessagesFilterActionGroup(project: Project) : DefaultActionGroup("Filter messages", true), DumbAware {
    init {
        templatePresentation.icon = ArendIcons.FILTER
        add(ArendMessagesFilterAction(project, GeneralError.Level.ERROR))
        add(ArendMessagesFilterAction(project, GeneralError.Level.WARNING))
        add(ArendMessagesFilterAction(project, GeneralError.Level.GOAL))
    }
}