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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.arend.InjectionTextLanguage
import org.arend.psi.ArendPsiFactory
import org.arend.repl.CommandHandler

class ArendReplExecutionHandler(project: Project) : BaseConsoleExecuteActionHandler(true) {
    private val repl = object : IntellijRepl(project) {
        override fun print(anything: Any?) =
            consoleView.print(anything.toString(), ConsoleViewContentType.NORMAL_OUTPUT)

        override fun eprintln(anything: Any?) =
            consoleView.print("$anything\n", ConsoleViewContentType.ERROR_OUTPUT)
    }

    val consoleView = LanguageConsoleBuilder()
        .psiFileFactory { v, p ->
            PsiManager.getInstance(p).findFile(v) ?: createFile(p, v)
        }
        .executionEnabled { true }
        .oneLineInput(false)
        .initActions(this, ArendReplFactory.ID)
        .build(project, InjectionTextLanguage.INSTANCE)

    override fun execute(text: String, console: LanguageConsoleView) {
        super.execute(text, console)
        repl.repl(text) { "" }
    }

    init {
        consoleView.isEditable = true
        consoleView.isConsoleEditorEnabled = true
        repl.initialize()
        resetRepl()
    }

    private fun createFile(p: Project, v: VirtualFile) = ArendPsiFactory(p)
        .createFromText(v.inputStream.reader().readText())

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