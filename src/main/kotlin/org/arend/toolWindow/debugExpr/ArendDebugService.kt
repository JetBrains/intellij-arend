package org.arend.toolWindow.debugExpr

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import org.arend.toolWindow.SimpleToolWindowService

class ArendDebugService(project: Project) : SimpleToolWindowService(project) {
    companion object Constants {
        const val TITLE = "Typecheck Debug"
        const val ID = "Arend.Typecheck.Debug"
    }

    override val title: String get() = TITLE

    fun createDebugger(name: String, debuggerFactory: (ToolWindow) -> CheckTypeDebugger): CheckTypeDebugger {
        val manager = ToolWindowManager.getInstance(project)
        val toolWindow = myToolWindow ?: registerToolWindow(manager).also { myToolWindow = it }
        val debugger = debuggerFactory(toolWindow)
        Disposer.register(toolWindow.disposable, debugger)
        val toolWindowPanel = SimpleToolWindowPanel(false, false)
        toolWindowPanel.setContent(debugger.splitter)
        // toolWindowPanel.toolbar = ActionManager.getInstance()
        //     .createActionToolbar(TITLE, , true).component
        val content = ContentFactory.SERVICE.getInstance()
            .createContent(toolWindowPanel.component, name, false)
        toolWindow.contentManager.addContent(content)
        content.preferredFocusableComponent = toolWindowPanel.content
        activate(toolWindow, manager)
        return debugger
    }
}