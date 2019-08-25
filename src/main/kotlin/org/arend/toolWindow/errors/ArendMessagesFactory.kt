package org.arend.toolWindow.errors

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory


class ArendMessagesFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        ServiceManager.getService(project, ArendMessagesService::class.java).initView(toolWindow)
    }

    override fun isDoNotActivateOnStart() = true
}