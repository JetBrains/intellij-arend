package org.arend.toolWindow.repl

import com.intellij.execution.console.BaseConsoleExecuteActionHandler
import com.intellij.execution.console.LanguageConsoleBuilder
import com.intellij.execution.console.LanguageConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import org.arend.ArendLanguage
import org.arend.psi.ArendFile
import org.arend.repl.CommandHandler
import org.arend.repl.action.NormalizeCommand
import org.arend.settings.ArendProjectSettings

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
        .initActions(this, ArendReplService.ID)
        .build(project, ArendLanguage.INSTANCE)

    val arendFile = consoleView.file as ArendFile

    override fun execute(text: String, console: LanguageConsoleView) {
        super.execute(text, console)
        if (repl.repl(text) { "" }) {
            toolWindow.hide()
            repl.clearScope()
            repl.resetCurrentLineScope(arendFile)
            resetRepl()
            saveSettings()
        }
    }

    init {
        consoleView.isEditable = true
        consoleView.isConsoleEditorEnabled = true
        Disposer.register(consoleView, Disposable(::saveSettings))
        val normalization = consoleView.project.service<ArendProjectSettings>().data
            .replNormalizationMode
        NormalizeCommand.INSTANCE.loadNormalize(normalization, repl, false)
        repl.withArendFile(arendFile)
        repl.initialize()
        resetRepl()
    }

    private fun saveSettings() {
        consoleView.project.service<ArendProjectSettings>().data
            .replNormalizationMode = repl.normalizationMode.toString()
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