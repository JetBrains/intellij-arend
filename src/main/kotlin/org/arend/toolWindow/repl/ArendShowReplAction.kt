package org.arend.toolWindow.repl

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import org.arend.ArendIcons

class ArendShowReplAction : AnAction(ArendIcons.REPL) {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.service<ArendReplService>()?.show()
    }
}