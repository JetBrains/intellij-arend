package org.arend.toolWindow.debugExpr

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import org.arend.toolWindow.SimpleToolWindowService

class ArendDebugService(project: Project) : SimpleToolWindowService(project) {
    companion object Constants {
        const val TITLE = "Typecheck Debug"
        const val ID = "Arend.Typecheck.Debug"

        val DEBUGGED_EXPRESSION = EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES
    }

    override val title: String get() = TITLE

    fun createDebugger(name: String, debugger: CheckTypeDebugger): CheckTypeDebugger {
        val manager = ToolWindowManager.getInstance(project)
        val toolWindow = myToolWindow
            ?: registerToolWindow(manager).also { myToolWindow = it }
        val toolWindowPanel = SimpleToolWindowPanel(false, false)
        toolWindowPanel.setContent(debugger.splitter)
        toolWindowPanel.toolbar = ActionManager.getInstance()
            .createActionToolbar(TITLE, debugger.createActionGroup(), true).component
        val content = ContentFactory.SERVICE.getInstance()
            .createContent(toolWindowPanel.component, name, false)
        Disposer.register(content, debugger)
        val contentManager = toolWindow.contentManager
        contentManager.addContent(content)
        contentManager.requestFocus(content, true)
        content.preferredFocusableComponent = toolWindowPanel.content
        activate(toolWindow, manager)
        return debugger
    }
}