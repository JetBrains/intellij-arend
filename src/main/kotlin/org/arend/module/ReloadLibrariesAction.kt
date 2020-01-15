package org.arend.module

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.components.service
import org.arend.psi.listener.ArendDefinitionChangeService
import org.arend.typechecking.ArendTypechecking
import org.arend.typechecking.TypeCheckingService


class ReloadLibrariesAction : AnAction("Reload Arend Libraries") {
    override fun actionPerformed(e: AnActionEvent) {
        val module = LangDataKeys.MODULE.getData(e.dataContext) ?: return
        if (!ArendModuleType.has(module)) return
        val project = module.project
        project.service<TypeCheckingService>().libraryManager.reloadInternalLibraries(ArendTypechecking.create(project))
        project.service<ArendDefinitionChangeService>().incModificationCount()
        DaemonCodeAnalyzer.getInstance(project).restart()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isVisible = ArendModuleType.has(LangDataKeys.MODULE.getData(e.dataContext))
    }
}