package org.arend.toolWindow.repl

import com.intellij.execution.console.BaseConsoleExecuteActionHandler
import com.intellij.execution.console.LanguageConsoleBuilder
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import org.arend.ArendLanguage
import org.arend.module.ArendModuleType
import org.arend.psi.ArendPsiFactory
import org.arend.ui.IconButton
import java.awt.Insets
import javax.swing.JLabel
import javax.swing.JPanel

class ArendReplExecutionHandler(project: Project) : BaseConsoleExecuteActionHandler(true) {
    private var repl: IntellijRepl? = null
    private val moduleSelection = ComboBox<Module>()
    private val refresh = IconButton(AllIcons.Actions.RunAll)
    private val rerun = IconButton(AllIcons.Actions.Restart)
    private val project get() = consoleView.project
    val disposable: Disposable get() = consoleView

    private val consoleView = LanguageConsoleBuilder()
        .psiFileFactory { v, p ->
            PsiManager.getInstance(p).findFile(v) ?: createFile(p, v)
        }
        .executionEnabled { true }
        .oneLineInput(false)
        .initActions(this, ArendReplFactory.ID)
        .build(project, ArendLanguage.INSTANCE)


    init {
        consoleView.isEditable = true
        consoleView.isConsoleEditorEnabled = true
    }

    val component = JPanel().apply {
        layout = GridLayoutManager(2, 4, Insets(0, 0, 0, 0), -1, -1)

        rerun.isEnabled = false
        add(consoleView.component, GridConstraints(1, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false))
        add(moduleSelection, GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false))
        val arendModuleLabel = JLabel()
        arendModuleLabel.text = "Arend Module:"
        arendModuleLabel.labelFor = moduleSelection
        add(arendModuleLabel, GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false))
        rerun.toolTipText = "Restart REPL with selected module"
        rerun.addActionListener { rerunAction() }
        add(rerun, GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false))
        refresh.toolTipText = "Refresh module list"
        refresh.addActionListener { refreshAction() }
        add(refresh, GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false))
    }

    private fun refreshAction() = ApplicationManager.getApplication().invokeLater {
        refresh.icon = AllIcons.Actions.Refresh
        val module = moduleSelection.selectedItem as? Module?
        if (module == null || module.isDisposed || !module.isLoaded || ArendModuleType.has(module)) {
            DialogBuilder(project)
                .title("Selection Error")
                .centerPanel(JLabel("Please select a valid Arend module!"))
                .okActionEnabled(true)
                .show()
            return@invokeLater
        }
        if (repl != null) consoleView.clear()
        repl = IntellijReplImpl(module)
    }

    private inner class IntellijReplImpl(module: Module) : IntellijRepl(module) {
        override fun print(anything: Any?) {
            consoleView.print(anything.toString(), ConsoleViewContentType.NORMAL_OUTPUT)
        }

        override fun eprintln(anything: Any?) {
            consoleView.print("$anything\n", ConsoleViewContentType.ERROR_OUTPUT)
        }
    }

    private fun rerunAction() = ApplicationManager.getApplication().invokeLater {
        moduleSelection.removeAllItems()
        ModuleManager.getInstance(project)
            .modules
            .asSequence()
            .filter { it.moduleTypeName == ArendModuleType.name }
            .forEach(moduleSelection::addItem)
        rerun.isEnabled = moduleSelection.itemCount > 0
    }

    private fun createFile(p: Project, v: VirtualFile) = ArendPsiFactory(p)
        .createFromText(v.inputStream.reader().readText())

    fun createActionGroup() = DefaultActionGroup(
        object : DumbAwareAction("Clear", null, AllIcons.Actions.GC) {
            override fun actionPerformed(event: AnActionEvent) = WriteCommandAction.writeCommandAction(project).run<Exception> {
                val document = consoleView.editorDocument
                document.deleteString(0, document.textLength)
            }
        }
    )
}