package org.arend.toolWindow.repl

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import org.arend.toolWindow.SimpleToolWindowService


@Service(Service.Level.PROJECT)
class ArendReplService(project: Project) : SimpleToolWindowService(project) {
    companion object Constants {
        const val TITLE = "Arend REPL"
        const val ID = "Arend.REPL"
    }

    override val title: String get() = TITLE

    private var myHandler: ArendReplExecutionHandler? = null

    fun show(): ArendReplExecutionHandler {
        val manager = ToolWindowManager.getInstance(project)
        val rawToolWindow = myToolWindow
        val rawHandler = myHandler
        // In fact, `rawToolWindow != null` should imply `rawHandler != null`
        if (rawToolWindow != null && rawHandler != null) {
            activate(rawToolWindow, manager)
            return rawHandler
        }
        val toolWindow = registerToolWindow(manager)
        val handler = ArendReplExecutionHandler(project, toolWindow)
        myToolWindow = toolWindow
        myHandler = handler
        Disposer.register(toolWindow.disposable, handler.consoleView)
        val toolWindowPanel = SimpleToolWindowPanel(false, false)
        toolWindowPanel.setContent(handler.consoleView.component)
        toolWindowPanel.toolbar = ActionManager.getInstance()
            .createActionToolbar(TITLE, handler.createActionGroup(), true).component
        val content = ContentFactory.getInstance()
            .createContent(toolWindowPanel.component, null, false)
        toolWindow.contentManager.addContent(content)
        content.preferredFocusableComponent = toolWindowPanel.content
        activate(toolWindow, manager)
        return handler
    }

    fun getRepl() = myHandler?.repl
}
