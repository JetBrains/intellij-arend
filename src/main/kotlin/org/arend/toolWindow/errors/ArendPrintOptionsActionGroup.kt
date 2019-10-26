package org.arend.toolWindow.errors

import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import org.arend.ArendIcons
import org.arend.core.expr.visitor.ToAbstractVisitor

class ArendPrintOptionsActionGroup(val project: Project, val kind: PrintOptionKind):
        DefaultActionGroup("${kind.kindName} pretty print options", true), DumbAware {
    private var actionMap = HashMap<ToAbstractVisitor.Flag, ArendPrintOptionsFilterAction>()

    init {
        templatePresentation.icon = ArendIcons.FILTER
        for (type in ToAbstractVisitor.Flag.values()) {
            val action = ArendPrintOptionsFilterAction(project, kind, type)
            add(action)
            actionMap[type] = action
        }
    }


}