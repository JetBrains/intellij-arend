package org.arend.toolWindow.repl

import com.intellij.execution.console.BaseConsoleExecuteActionHandler
import com.intellij.execution.console.LanguageConsoleBuilder
import com.intellij.execution.console.LanguageConsoleImpl
import com.intellij.execution.console.LanguageConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import org.arend.ArendIcons
import org.arend.ArendLanguage
import org.arend.ext.core.ops.NormalizationMode
import org.arend.psi.ArendFile
import org.arend.repl.CommandHandler
import org.arend.settings.ArendProjectSettings
import org.arend.toolWindow.errors.ArendPrintOptionsActionGroup
import org.arend.toolWindow.errors.PrintOptionKind
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class ArendReplExecutionHandler(
    project: Project,
    private val toolWindow: ToolWindow
) : BaseConsoleExecuteActionHandler(true) {
    val repl = object : IntellijRepl(this, project) {
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
            closeRepl()
        }
    }

    init {
        consoleView.isEditable = true
        consoleView.isConsoleEditorEnabled = true
        Disposer.register(consoleView, Disposable(::saveSettings))
        val settings = settings()
        if (settings.replPrintingOptionsFilterSet !== repl.prettyPrinterFlags) {
            // Bind the settings with the repl config
            repl.prettyPrinterFlags.clear()
            repl.prettyPrinterFlags.addAll(settings.replPrintingOptionsFilterSet)
            settings.replPrintingOptionsFilterSet = repl.prettyPrinterFlags
        }
        val normalization = settings.data.replNormalizationMode.toUpperCase()
        try {
            repl.normalizationMode = NormalizationMode.valueOf(normalization)
        } catch (e: IllegalArgumentException) {
            repl.normalizationMode = null
        }
        repl.initialize()
        resetRepl()
    }

    private fun saveSettings() {
        settings().data.replNormalizationMode = repl.normalizationMode.toString()
    }

    private fun settings() =
        consoleView.project.service<ArendProjectSettings>()

    private fun closeRepl() {
        toolWindow.hide()
        repl.clearScope()
        repl.resetCurrentLineScope()
        resetRepl()
        saveSettings()
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
        object : DumbAwareAction("Clear", null, ArendIcons.CLEAR) {
            override fun actionPerformed(event: AnActionEvent) = ApplicationManager.getApplication()
                .invokeLater(this@ArendReplExecutionHandler::resetRepl)
        }.apply {
            registerCustomShortcutSet(CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK)), consoleView.preferredFocusableComponent)
        },
        object : DumbAwareAction("Cancel", null, ArendIcons.CANCEL) {
            override fun actionPerformed(e: AnActionEvent) {
                val view = consoleView
                if (view is LanguageConsoleImpl) {
                    view.prepareExecuteAction(true, true, true)
                } else {
                    view.setInputText("")
                }
            }
        }.apply {
            registerCustomShortcutSet(CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK)), consoleView.preferredFocusableComponent)
        },
        object : DumbAwareAction("Close", null, ArendIcons.CLOSE) {
            override fun actionPerformed(e: AnActionEvent) {
                if (consoleView.editorDocument.textLength == 0) {
                    closeRepl()
                }
            }
        }.apply {
            registerCustomShortcutSet(CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK)), consoleView.preferredFocusableComponent)
        },
        ArendPrintOptionsActionGroup(consoleView.project, PrintOptionKind.REPL_PRINT_OPTIONS),
    )
}