package org.arend.module

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import org.arend.typechecking.TypeCheckingService


class ReloadLibrariesAction : AnAction("Reload Arend Libraries") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Reloading Arend libraries", false) {
            override fun run(indicator: ProgressIndicator) {
                runReadAction {
                    project.service<TypeCheckingService>().reloadInternal()
                }
            }
        })
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isVisible = ArendModuleType.has(LangDataKeys.MODULE.getData(e.dataContext))
    }
}