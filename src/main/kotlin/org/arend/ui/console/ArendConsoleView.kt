package org.arend.ui.console

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import org.arend.ArendIcons
import org.arend.injection.InjectedArendEditor

class ArendConsoleView(project: Project) {
    val toolWindow = ToolWindowManager.getInstance(project).registerToolWindow(RegisterToolWindowTask("Arend Console", ToolWindowAnchor.RIGHT, canWorkInDumbMode = false))
    val editor = InjectedArendEditor(project, "Arend Console", null)

    init {
        toolWindow.setIcon(ArendIcons.CONSOLE)
        val contentManager = this.toolWindow.contentManager
        contentManager.addContent(contentManager.factory.createContent(editor.component, "", false))
    }
}