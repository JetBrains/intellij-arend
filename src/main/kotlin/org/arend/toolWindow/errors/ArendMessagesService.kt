package org.arend.toolWindow.errors

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import org.arend.typechecking.error.ErrorService


class ArendMessagesService(private val project: Project) {
    private var view: ArendMessagesView? = null

    private fun activate(project: Project) {
        runInEdt {
            ToolWindowManager.getInstance(project).getToolWindow("Arend Errors")?.activate({
                val service = project.service<ArendMessagesService>()
                val view = service.view
                if (view != null) {
                    view.update()
                    view.tree.selectFirst()
                }
            }, false, false)
        }
    }

    fun initView(toolWindow: ToolWindow) {
        view = ArendMessagesView(project, toolWindow)
    }

    fun update() {
        val view = view
        if (view == null) {
            if (project.service<ErrorService>().hasErrors) {
                activate(project)
            }
        } else {
            view.update()
        }
    }
}