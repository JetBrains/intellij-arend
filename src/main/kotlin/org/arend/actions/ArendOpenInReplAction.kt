package org.arend.actions

import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import org.arend.psi.ArendFile
import org.arend.toolWindow.repl.ArendReplService

class ArendOpenInReplAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabledAndVisible = module(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val module = module(e) ?: return
        val service: ArendReplService = project.service()
        val handler = service.show()
        val modulePath = module.modulePath
        // Is there a better wat to execute code in Repl?
        handler.consoleView.print("Imported $modulePath from editor.\n", ConsoleViewContentType.NORMAL_OUTPUT)
        handler.repl.repl("\\import $modulePath\n") { "" }
    }

    private fun module(e: AnActionEvent) =
        (CommonDataKeys.PSI_FILE.getData(e.dataContext) as? ArendFile)?.moduleLocation
}
