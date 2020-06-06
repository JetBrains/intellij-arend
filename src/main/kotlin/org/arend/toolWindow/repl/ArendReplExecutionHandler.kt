package org.arend.toolWindow.repl

import com.intellij.execution.console.BaseConsoleExecuteActionHandler
import com.intellij.execution.console.LanguageConsoleBuilder
import com.intellij.execution.console.LanguageConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import org.arend.ArendLanguage
import org.arend.psi.ArendFile
import org.arend.repl.CommandHandler

class ArendReplExecutionHandler(
    project: Project,
    private val toolWindow: ToolWindow
) : BaseConsoleExecuteActionHandler(true) {
    private val repl = object : IntellijRepl(project) {
        override fun print(anything: Any?) {
            val s = anything.toString()
            when {
                s.startsWith("[INFO]") -> consoleView.print(s, ConsoleViewContentType.LOG_INFO_OUTPUT)
                s.startsWith("[ERROR]") || s.startsWith("[FATAL]") -> consoleView.print(s, ConsoleViewContentType.ERROR_OUTPUT)
                s.startsWith("[WARN]") || s.startsWith("[WARNING]") -> consoleView.print(s, ConsoleViewContentType.LOG_WARNING_OUTPUT)
                else -> consoleView.print(s, ConsoleViewContentType.NORMAL_OUTPUT)
            }
        }

        override fun eprintln(anything: Any?) =
            consoleView.print("$anything\n", ConsoleViewContentType.ERROR_OUTPUT)
    }

    val consoleView = LanguageConsoleBuilder()
        .executionEnabled { true }
        .oneLineInput(false)
        .initActions(this, ArendReplFactory.ID)
        .build(project, ArendLanguage.INSTANCE)

    val arendFile = consoleView.file as ArendFile

    override fun execute(text: String, console: LanguageConsoleView) {
        super.execute(text, console)
        if (repl.repl(text) { "" }) toolWindow.hide()
    }

    init {
        consoleView.isEditable = true
        consoleView.isConsoleEditorEnabled = true
        arendFile.isFragment = true
        repl.initialize()
        resetRepl()
    }

    private fun resetRepl() {
        consoleView.clear()
        consoleView.print("Type ", ConsoleViewContentType.NORMAL_OUTPUT)
        consoleView.printHyperlink(":?") {
            CommandHandler.HELP_COMMAND_INSTANCE("", repl) { "" }
        }
        consoleView.print(" for help.\n", ConsoleViewContentType.NORMAL_OUTPUT)
    }

    fun createActionGroup() = DefaultActionGroup(
        object : DumbAwareAction("Clear", null, AllIcons.Actions.GC) {
            override fun actionPerformed(event: AnActionEvent) = ApplicationManager.getApplication()
                .invokeLater(this@ArendReplExecutionHandler::resetRepl)
        }
    )
}