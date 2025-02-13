package org.arend.scratch

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.PsiManager
import org.arend.psi.ArendFile
import org.arend.psi.module
import org.jetbrains.kotlin.idea.scratch.ScratchFile
import org.jetbrains.kotlin.idea.scratch.ScratchFileListener
import org.jetbrains.kotlin.idea.scratch.ScratchFileModuleInfoProvider

class ArendScratchFileModuleInfoProvider : ScratchFileListener {
    companion object {
        private val LOG = logger<ScratchFileModuleInfoProvider>()
    }

    override fun fileCreated(file: ScratchFile) {
        val arendFile = file.getPsiFile() as? ArendFile? ?: return
        val virtualFile = arendFile.virtualFile ?: return
        val project = arendFile.project

        if (virtualFile.extension != SCRATCH_SUFFIX) {
            LOG.error("Arend Scratch file should have .ars extension. Cannot add scratch panel for ${virtualFile.path}")
            return
        }

        file.addModuleListener { psiFile, module ->
            project.service<ArendScratchModuleService>().updateFileModule(psiFile as ArendFile, module)
            invokeLater {
                WriteCommandAction.runWriteCommandAction(project) {
                    invokeLater {
                        PsiManager.getInstance(project).dropPsiCaches()
                        DaemonCodeAnalyzer.getInstance(project).restart(psiFile)
                    }
                }
            }
        }
        file.setModule(arendFile.module)
    }
}
