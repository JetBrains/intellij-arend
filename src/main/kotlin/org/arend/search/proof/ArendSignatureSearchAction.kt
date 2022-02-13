package org.arend.search.proof

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service

class ArendSignatureSearchAction : AnAction(){
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.service<SignatureSearchService>()?.show(e)
    }
}