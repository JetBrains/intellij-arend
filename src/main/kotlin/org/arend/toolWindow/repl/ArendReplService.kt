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
        // // Hacking a behaviour of LanguageConsoleViewBuilder.build
        // val VIRTUAL_FILE_NAME = ArendLanguage.INSTANCE.displayName + " Console"
    }

    private var myToolWindow: ToolWindow? = null

    fun show() {
        val manager = ToolWindowManager.getInstance(project)
        val rawToolWindow = myToolWindow
        if (rawToolWindow != null) {
            rawToolWindow.activate {
                manager.focusManager.requestFocusInProject(rawToolWindow.component, project)
            }
            return
        }
        val toolWindow = manager.registerToolWindow(RegisterToolWindowTask(TITLE, ToolWindowAnchor.BOTTOM, canWorkInDumbMode = false))
        myToolWindow = toolWindow
        val handler = ArendReplExecutionHandler(project, toolWindow)
        Disposer.register(toolWindow.disposable, handler.consoleView)
        val toolWindowPanel = SimpleToolWindowPanel(false, false)
        toolWindowPanel.setContent(handler.consoleView.component)
        toolWindowPanel.toolbar = ActionManager.getInstance()
            .createActionToolbar(TITLE, handler.createActionGroup(), true).component
        val content = ContentFactory.SERVICE.getInstance()
            .createContent(toolWindowPanel.component, null, false)
        toolWindow.contentManager.addContent(content)
        manager.focusManager.requestFocusInProject(toolWindow.component, project)
    }
}
