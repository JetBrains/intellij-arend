package org.arend.ui.console

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.arend.injection.InjectedArendEditor
import org.arend.toolWindow.errors.ArendPrintOptionsActionGroup
import org.arend.toolWindow.errors.PrintOptionKind

class ArendConsoleViewEditor(project: Project) : InjectedArendEditor(project, ArendConsoleView.CONSOLE_ID, null) {
    override val printOptionKind: PrintOptionKind = PrintOptionKind.CONSOLE_PRINT_OPTIONS

    init {
        if (editor != null) {
            actionGroup.add(ArendClearConsoleAction(project, editor.contentComponent))
            actionGroup.add(ArendPrintOptionsActionGroup(project, PrintOptionKind.CONSOLE_PRINT_OPTIONS, {
                project.service<ArendConsoleService>().updateText()
            }))
        }
    }
}