package org.arend.toolWindow.repl

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory


class ArendReplService(private val project: Project) {
    companion object Constants {
        const val TITLE = "Arend REPL"
        const val ID = "Arend.REPL"
    }

    private var myToolWindow: ToolWindow? = null
    private var handler: ArendReplExecutionHandler? = null

    fun show() {
        val manager = ToolWindowManager.getInstance(project)
        val rawToolWindow = myToolWindow
        if (rawToolWindow != null) return activate(rawToolWindow, manager)
        val toolWindow = manager.registerToolWindow(RegisterToolWindowTask(TITLE, ToolWindowAnchor.BOTTOM, canWorkInDumbMode = false))
        myToolWindow = toolWindow
        val handler = ArendReplExecutionHandler(project, toolWindow)
        this.handler = handler
        Disposer.register(toolWindow.disposable, handler.consoleView)
        val toolWindowPanel = SimpleToolWindowPanel(false, false)
        toolWindowPanel.setContent(handler.consoleView.component)
        toolWindowPanel.toolbar = ActionManager.getInstance()
            .createActionToolbar(TITLE, handler.createActionGroup(), true).component
        val content = ContentFactory.SERVICE.getInstance()
            .createContent(toolWindowPanel.component, null, false)
        toolWindow.contentManager.addContent(content)
        content.preferredFocusableComponent = toolWindowPanel.content
        activate(toolWindow, manager)
    }

    private fun activate(toolWindow: ToolWindow, manager: ToolWindowManager) =
        toolWindow.activate {
            manager.focusManager.requestFocusInProject(toolWindow.component, project)
        }
}
