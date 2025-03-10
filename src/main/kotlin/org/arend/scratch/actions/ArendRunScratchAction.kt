package org.arend.scratch.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbService
import com.intellij.task.ProjectTaskManager
import com.intellij.task.impl.ProjectTaskManagerImpl
import org.jetbrains.kotlin.idea.KotlinJvmBundle
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.configuration.CompositeScriptConfigurationManager
import org.jetbrains.kotlin.idea.scratch.ScratchFile
import org.jetbrains.kotlin.idea.scratch.SequentialScratchExecutor
import org.jetbrains.kotlin.idea.scratch.actions.ScratchCompilationSupport
import org.jetbrains.kotlin.utils.addToStdlib.UnsafeCastFunction
import org.jetbrains.kotlin.utils.addToStdlib.cast

class ArendRunScratchAction : ArendScratchAction(
    KotlinJvmBundle.message("scratch.run.button"),
    AllIcons.Actions.Execute
) {

    init {
        KeymapManager.getInstance().activeKeymap.getShortcuts("Kotlin.RunScratch").firstOrNull()?.let {
            templatePresentation.text += " (${KeymapUtil.getShortcutText(it)})"
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val scratchFile = e.currentScratchFile ?: return

        Handler.doAction(scratchFile, false)
    }

    object Handler {
        @OptIn(UnsafeCastFunction::class)
        fun doAction(scratchFile: ScratchFile, isAutoRun: Boolean) {
            val project = scratchFile.project
            val isRepl = scratchFile.options.isRepl
            val executor = (if (isRepl) scratchFile.replScratchExecutor else scratchFile.compilingScratchExecutor) ?: return


            fun executeScratch() {
                try {
                    if (isAutoRun && executor is SequentialScratchExecutor) {
                        executor.executeNew()
                    } else {
                        executor.execute()
                    }
                } catch (ex: Throwable) {
                    executor.errorOccurs(KotlinJvmBundle.message("exception.occurs.during.run.scratch.action"), ex, true)
                }
            }

            val isMakeBeforeRun = scratchFile.options.isMakeBeforeRun

            ScriptConfigurationManager.getInstance(project).cast<CompositeScriptConfigurationManager>()
                .updateScriptDependenciesIfNeeded(scratchFile.file)

            val module = scratchFile.module

            if (!isAutoRun && module != null && isMakeBeforeRun) {
                ProjectTaskManagerImpl.putBuildOriginator(project, this.javaClass)
                ProjectTaskManager.getInstance(project).build(module).onSuccess { executionResult ->
                    if (executionResult.isAborted || executionResult.hasErrors()) {
                        executor.errorOccurs(KotlinJvmBundle.message("there.were.compilation.errors.in.module.0", module.name))
                    }

                    if (DumbService.isDumb(project)) {
                        DumbService.getInstance(project).smartInvokeLater {
                            executeScratch()
                        }
                    } else {
                        executeScratch()
                    }
                }
            } else {
                executeScratch()
            }
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)

        e.presentation.isEnabled = !ScratchCompilationSupport.isAnyInProgress()

        if (e.presentation.isEnabled) {
            e.presentation.text = templatePresentation.text
        } else {
            e.presentation.text = KotlinJvmBundle.message("other.scratch.file.execution.is.in.progress")
        }

        val scratchFile = e.currentScratchFile ?: return

        e.presentation.isVisible = !ScratchCompilationSupport.isInProgress(scratchFile)
    }
}
