package org.arend.toolWindow.errors

import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import org.arend.ArendIcons
import org.arend.ext.prettyprinting.PrettyPrinterFlag

class ArendPrintOptionsActionGroup(val project: Project, val kind: PrintOptionKind, isEnabled: Boolean):
        DefaultActionGroup("${kind.kindName}s pretty printer options", true), DumbAware {
    private var actionMap = HashMap<PrettyPrinterFlag, ArendPrintOptionsFilterAction>()

    init {
        templatePresentation.icon = ArendIcons.SHOW
        if (isEnabled) {
            for (type in PrettyPrinterFlag.values()) {
                val action = ArendPrintOptionsFilterAction(project, kind, type)
                add(action)
                actionMap[type] = action
            }
        }
    }
}