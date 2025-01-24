package org.arend.scratch.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.kotlin.idea.KotlinJvmBundle
import org.jetbrains.kotlin.idea.scratch.actions.ScratchCompilationSupport

class ArendStopScratchAction : ArendScratchAction(
    KotlinJvmBundle.message("scratch.stop.button"),
    AllIcons.Actions.Suspend
) {

    override fun actionPerformed(e: AnActionEvent) {
        ScratchCompilationSupport.forceStop()
    }

    override fun update(e: AnActionEvent) {
        super.update(e)

        val scratchFile = e.currentScratchFile ?: return

        e.presentation.isEnabledAndVisible = ScratchCompilationSupport.isInProgress(scratchFile)
    }
}
