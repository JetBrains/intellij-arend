package org.arend.toolWindow.errors

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory


class ArendMessagesFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        project.getService(ArendMessagesService::class.java).initView(toolWindow)
    }

    companion object {
        const val TOOL_WINDOW_ID = "Arend Messages"
    }
}