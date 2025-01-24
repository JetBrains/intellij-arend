package org.arend.scratch

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import org.arend.scratch.actions.ArendClearScratchAction
import org.arend.scratch.actions.ArendRunScratchAction
import org.arend.scratch.actions.ArendStopScratchAction
import org.jetbrains.kotlin.idea.KotlinJvmBundle
import org.jetbrains.kotlin.idea.scratch.ScratchFile
import org.jetbrains.kotlin.idea.scratch.ScratchFileAutoRunner
import org.jetbrains.kotlin.idea.scratch.output.ScratchOutputHandlerAdapter
import org.jetbrains.kotlin.idea.scratch.ui.ModulesComboBoxAction
import org.jetbrains.kotlin.idea.scratch.ui.SmallBorderCheckboxAction

class ArendScratchTopPanel(val scratchFile: ScratchFile) {
    private val moduleChooserAction: ModulesComboBoxAction = ModulesComboBoxAction(scratchFile)
    val actionsToolbar: ActionToolbar

    init {
        setupTopPanelUpdateHandlers()

        val toolbarGroup = DefaultActionGroup().apply {
            add(ArendRunScratchAction())
            add(ArendStopScratchAction())
            addSeparator()
            add(ArendClearScratchAction())
            addSeparator()
            add(moduleChooserAction)
            add(IsMakeBeforeRunAction())
            addSeparator()
            add(IsInteractiveCheckboxAction())
            addSeparator()
            add(IsReplCheckboxAction())
        }

        actionsToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, toolbarGroup, true)
    }

    private fun setupTopPanelUpdateHandlers() {
        scratchFile.addModuleListener { _, _ -> updateToolbar() }

        val toolbarHandler = createUpdateToolbarHandler()
        scratchFile.replScratchExecutor?.addOutputHandler(toolbarHandler)
        scratchFile.compilingScratchExecutor?.addOutputHandler(toolbarHandler)
    }

    private fun createUpdateToolbarHandler(): ScratchOutputHandlerAdapter {
        return object : ScratchOutputHandlerAdapter() {
            override fun onStart(file: ScratchFile) {
                updateToolbar()
            }

            override fun onFinish(file: ScratchFile) {
                updateToolbar()
            }
        }
    }

    private fun updateToolbar() {
        ApplicationManager.getApplication().invokeLater {
            actionsToolbar.updateActionsImmediately()
        }
    }

    private inner class IsMakeBeforeRunAction : SmallBorderCheckboxAction(KotlinJvmBundle.message("scratch.make.before.run.checkbox")) {
        override fun update(e: AnActionEvent) {
            super.update(e)
            e.presentation.isVisible = scratchFile.module != null
            e.presentation.description = scratchFile.module?.let { selectedModule ->
                KotlinJvmBundle.message("scratch.make.before.run.checkbox.description", selectedModule.name)
            }
        }

        override fun isSelected(e: AnActionEvent): Boolean {
            return scratchFile.options.isMakeBeforeRun
        }

        override fun setSelected(e: AnActionEvent, isMakeBeforeRun: Boolean) {
            scratchFile.saveOptions { copy(isMakeBeforeRun = isMakeBeforeRun) }
        }

        override fun getActionUpdateThread() = ActionUpdateThread.BGT
    }

    private inner class IsInteractiveCheckboxAction : SmallBorderCheckboxAction(
        text = KotlinJvmBundle.message("scratch.is.interactive.checkbox"),
        description = KotlinJvmBundle.message("scratch.is.interactive.checkbox.description", ScratchFileAutoRunner.AUTO_RUN_DELAY_IN_SECONDS)
    ) {
        override fun isSelected(e: AnActionEvent): Boolean {
            return scratchFile.options.isInteractiveMode
        }

        override fun setSelected(e: AnActionEvent, isInteractiveMode: Boolean) {
            scratchFile.saveOptions { copy(isInteractiveMode = isInteractiveMode) }
        }

        override fun getActionUpdateThread() = ActionUpdateThread.BGT
    }

    private inner class IsReplCheckboxAction : SmallBorderCheckboxAction(
        text = KotlinJvmBundle.message("scratch.is.repl.checkbox"),
        description = KotlinJvmBundle.message("scratch.is.repl.checkbox.description")
    ) {
        override fun isSelected(e: AnActionEvent): Boolean {
            return scratchFile.options.isRepl
        }

        override fun setSelected(e: AnActionEvent, isRepl: Boolean) {
            scratchFile.saveOptions { copy(isRepl = isRepl) }

            if (isRepl) {
                // TODO start REPL process when checkbox is selected to speed up execution
                // Now it is switched off due to KT-18355: REPL process is keep alive if no command is executed
                //scratchFile.replScratchExecutor?.start()
            } else {
                scratchFile.replScratchExecutor?.stop()
            }
        }

        override fun getActionUpdateThread() = ActionUpdateThread.BGT
    }
}