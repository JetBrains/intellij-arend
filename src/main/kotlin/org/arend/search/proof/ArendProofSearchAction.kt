package org.arend.search.proof

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service

class ArendProofSearchAction : AnAction(){
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.service<ProofSearchService>()?.show(e)
    }
}