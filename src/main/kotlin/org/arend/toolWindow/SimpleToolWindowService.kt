package org.arend.toolWindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import org.arend.toolWindow.repl.ArendReplService

abstract class SimpleToolWindowService(@JvmField protected val project: Project) {
    @JvmField protected var myToolWindow: ToolWindow? = null

    protected fun registerToolWindow(manager: ToolWindowManager) =
        manager.registerToolWindow(RegisterToolWindowTask(ArendReplService.TITLE, ToolWindowAnchor.BOTTOM, canWorkInDumbMode = false))

    protected fun activate(toolWindow: ToolWindow, manager: ToolWindowManager) =
        toolWindow.activate {
            manager.focusManager.requestFocusInProject(toolWindow.component, project)
        }
}