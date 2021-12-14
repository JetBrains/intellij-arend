package org.arend.toolWindow.errors

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.BooleanProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import org.arend.settings.ArendProjectSettings
import org.arend.typechecking.error.ErrorService


class ArendMessagesService(private val project: Project) {
    var view: ArendMessagesView? = null
        private set
    var isGoalTextPinned: Boolean = false
    var isAutoClearGoals: BooleanProperty =
            AtomicBooleanProperty(project.service<ArendProjectSettings>().data.isAutoClearGoals).apply {
                afterChange { newValue -> project.service<ArendProjectSettings>().data.isAutoClearGoals = newValue }
            }
    var isShowErrorsPanel: BooleanProperty =
            AtomicBooleanProperty(project.service<ArendProjectSettings>().data.isShowErrorsPanel).apply {
                afterChange { newValue -> project.service<ArendProjectSettings>().data.isShowErrorsPanel = newValue }
            }

    fun activate(project: Project, selectFirst: Boolean) {
        runInEdt {
            ToolWindowManager.getInstance(project).getToolWindow("Arend Messages")?.activate(if (selectFirst) Runnable {
                val service = project.service<ArendMessagesService>()
                val view = service.view
                if (view != null) {
                    view.update()
                    view.tree.selectFirst()
                }
            } else null, false, false)
        }
    }

    fun initView(toolWindow: ToolWindow) {
        view = ArendMessagesView(project, toolWindow)
    }

    fun update() {
        val view = view
        if (view == null) {
            if (project.service<ErrorService>().hasErrors) {
                activate(project, true)
            }
        } else {
            view.update()
        }
    }

    fun updateEditor() {
        view?.updateEditor()
    }

    fun updateGoalText() {
        view?.updateGoalText()
    }

    fun updateErrorText() {
        view?.updateErrorText()
    }

    fun clearGoalText() {
        view?.clearGoalText()
    }
}