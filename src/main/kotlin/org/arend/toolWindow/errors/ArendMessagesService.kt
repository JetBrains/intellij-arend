package org.arend.toolWindow.errors

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.MutableBooleanProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import org.arend.settings.ArendProjectSettings
import org.arend.typechecking.error.ErrorService


class ArendMessagesService(private val project: Project) {
    var view: ArendMessagesView? = null
        private set
    var isGoalTextPinned: Boolean = false
    var isErrorTextPinned: Boolean = false
    private val arendProjectSettings = project.service<ArendProjectSettings>().apply {
        data.isShowImplicitGoals = true
    }

    var isShowImplicitGoals: MutableBooleanProperty =
            AtomicBooleanProperty(arendProjectSettings.data.isShowImplicitGoals).apply {
                afterChange { arendProjectSettings.data.isShowImplicitGoals = it }
            }

    var isShowErrorsPanel: MutableBooleanProperty =
            AtomicBooleanProperty(arendProjectSettings.data.isShowErrorsPanel).apply {
                afterChange { arendProjectSettings.data.isShowErrorsPanel = it }
            }
    var isShowGoalsInErrorsPanel: MutableBooleanProperty =
            AtomicBooleanProperty(arendProjectSettings.data.isShowGoalsInErrorsPanel).apply {
                afterChange { arendProjectSettings.data.isShowGoalsInErrorsPanel = it }
            }

    fun activate(project: Project, selectFirst: Boolean) {
        runInEdt {
            ToolWindowManager.getInstance(project).getToolWindow(ArendMessagesFactory.TOOL_WINDOW_ID)?.activate(if (selectFirst) Runnable {
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

    fun updateEditors() {
        view?.updateEditors()
    }

    fun updateGoalText() {
        view?.updateGoalText()
    }

    fun updateErrorText() {
        view?.updateErrorText()
    }

    fun clearGoalEditor() {
        view?.clearGoalEditor()
    }
}