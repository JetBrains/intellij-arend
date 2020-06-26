package org.arend.toolWindow.repl

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service

class ArendShowReplAction : AnAction(AllIcons.Actions.Run_anything) {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.service<ArendReplService>()?.show()
    }
}