package org.arend.toolWindow.tracer

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import com.intellij.xdebugger.ui.DebuggerColors
import org.arend.toolWindow.SimpleToolWindowService

class ArendTracerService(project: Project) : SimpleToolWindowService(project) {
    companion object Constants {
        const val TITLE = "Arend Tracer"

        val TRACED_EXPRESSION = DebuggerColors.SMART_STEP_INTO_SELECTION!!
    }

    override val title: String get() = TITLE

    fun createTracer(name: String, tracer: TracingTypechecker): TracingTypechecker {
        val manager = ToolWindowManager.getInstance(project)
        val toolWindow = myToolWindow
            ?: registerToolWindow(manager).also { myToolWindow = it }
        val toolWindowPanel = SimpleToolWindowPanel(false, false)
        toolWindowPanel.setContent(tracer.splitter)
        toolWindowPanel.toolbar = ActionManager.getInstance()
            .createActionToolbar(TITLE, tracer.createActionGroup(), true).component
        val content = ContentFactory.SERVICE.getInstance()
            .createContent(toolWindowPanel.component, name, false)
        Disposer.register(content, tracer)
        val contentManager = toolWindow.contentManager
        contentManager.addContent(content)
        contentManager.setSelectedContent(content)
        contentManager.requestFocus(content, true)
        content.preferredFocusableComponent = toolWindowPanel.content
        activate(toolWindow, manager)
        return tracer
    }
}