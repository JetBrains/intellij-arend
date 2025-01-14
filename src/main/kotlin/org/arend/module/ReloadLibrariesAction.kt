package org.arend.module

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service


class ReloadLibrariesAction : AnAction("Reload Arend Libraries") {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.service<ReloadLibrariesService>()?.reload(true)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}