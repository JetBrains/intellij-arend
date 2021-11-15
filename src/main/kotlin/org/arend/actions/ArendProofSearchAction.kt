package org.arend.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import org.arend.search.proof.ProofSearchService

class ArendProofSearchAction : AnAction(){
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.service<ProofSearchService>()?.show(e)
    }

}