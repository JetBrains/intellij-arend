package org.arend.scratch.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.kotlin.idea.KotlinJvmBundle

class ArendClearScratchAction : ArendScratchAction(
    KotlinJvmBundle.message("scratch.clear.button"),
    AllIcons.Actions.GC
) {
    override fun actionPerformed(e: AnActionEvent) {
        val scratchEditor = e.currentScratchEditor ?: return

        scratchEditor.clearOutputHandlers()
    }
}
