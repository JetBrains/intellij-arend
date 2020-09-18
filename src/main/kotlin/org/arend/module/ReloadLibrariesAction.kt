package org.arend.module

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.components.service
import org.arend.typechecking.TypeCheckingService


class ReloadLibrariesAction : AnAction("Reload Arend Libraries") {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.service<TypeCheckingService>()?.reload(true)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isVisible = ArendModuleType.has(LangDataKeys.MODULE.getData(e.dataContext))
    }
}