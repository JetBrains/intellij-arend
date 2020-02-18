package org.arend.toolWindow.errors

import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import org.arend.ArendIcons
import org.arend.core.expr.visitor.ToAbstractVisitor

class ArendPrintOptionsActionGroup(val project: Project, val kind: PrintOptionKind, isEnabled: Boolean = true):
        DefaultActionGroup("${kind.kindName}s pretty printer options", true), DumbAware {
    init {
        templatePresentation.icon = ArendIcons.SHOW
        if (isEnabled) {
            for (type in ToAbstractVisitor.Flag.values()) {
                add(ArendPrintOptionsFilterAction(project, kind, type))
            }
        }
    }
}