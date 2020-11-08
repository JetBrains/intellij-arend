package org.arend.toolWindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import org.arend.toolWindow.repl.ArendReplService

abstract class SimpleToolWindowService(@JvmField protected val project: Project) {
    @JvmField protected var myToolWindow: ToolWindow? = null

    abstract val title: String

    protected fun registerToolWindow(manager: ToolWindowManager) =
        manager.registerToolWindow(RegisterToolWindowTask(title, ToolWindowAnchor.BOTTOM, canWorkInDumbMode = false))

    protected fun activate(toolWindow: ToolWindow, manager: ToolWindowManager) =
        toolWindow.activate {
            manager.focusManager.requestFocusInProject(toolWindow.component, project)
        }
}