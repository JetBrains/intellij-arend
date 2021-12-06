package org.arend.ui.console

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import org.arend.ArendIcons

class ArendConsoleView(project: Project) : ProjectManagerListener {
    val toolWindow = ToolWindowManager.getInstance(project).registerToolWindow(RegisterToolWindowTask(CONSOLE_ID, ToolWindowAnchor.RIGHT, canWorkInDumbMode = false))
    val editor = ArendConsoleViewEditor(project)

    init {
        ProjectManager.getInstance().addProjectManagerListener(project, this)
        toolWindow.setIcon(ArendIcons.CONSOLE)
        val contentManager = this.toolWindow.contentManager
        contentManager.addContent(contentManager.factory.createContent(editor.component, "", false))
    }

    override fun projectClosing(project: Project) {
        editor.release()
    }

    companion object {
        const val CONSOLE_ID = "Arend Console"
    }
}