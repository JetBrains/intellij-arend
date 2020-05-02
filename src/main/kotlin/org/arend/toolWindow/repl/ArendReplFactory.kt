package org.arend.toolWindow.repl

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory


class ArendReplFactory : ToolWindowFactory {
    companion object Constants {
        const val TITLE = "Arend REPL"
        const val ID = "Arend.REPL"
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val handler = ArendReplExecutionHandler(project)
        Disposer.register(toolWindow.disposable, handler.consoleView)
        val toolWindowPanel = SimpleToolWindowPanel(false, false)
        toolWindowPanel.setContent(handler.consoleView.component)
        toolWindowPanel.toolbar = ActionManager.getInstance()
            .createActionToolbar(TITLE, handler.createActionGroup(), true).component
        val content = ContentFactory.SERVICE.getInstance()
            .createContent(toolWindowPanel.component, null, false)
        toolWindow.contentManager.addContent(content)
    }
}
