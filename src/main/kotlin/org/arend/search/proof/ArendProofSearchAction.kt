package org.arend.search.proof

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.components.service
import org.arend.ArendIcons
import org.arend.ArendLanguage

class ArendProofSearchAction : AnAction(ArendIcons.AREND) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.getData(LangDataKeys.LANGUAGE) == ArendLanguage.INSTANCE
    }

    override fun actionPerformed(e: AnActionEvent) {
        e.project?.service<ProofSearchService>()?.show(e)
    }
}