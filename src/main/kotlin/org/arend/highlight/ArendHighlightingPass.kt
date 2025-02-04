package org.arend.highlight

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import org.arend.IArendFile
import org.arend.psi.*
import org.arend.server.ArendServerService
import org.arend.toolWindow.errors.ArendMessagesService
import org.arend.typechecking.*

class ArendHighlightingPass(file: IArendFile, editor: Editor, textRange: TextRange)
    : BasePass(file, editor, "Arend resolver annotator", textRange) {

    private val module = (file as? ArendFile)?.moduleLocation

    public override fun collectInformationWithProgress(progress: ProgressIndicator) {
        progress.isIndeterminate = true

        if (module != null) {
            myProject.service<ArendServerService>().server.getCheckerFor(listOf(module)).resolveModules(this, ProgressCancellationIndicator(progress), HighlightingResolverListener(this, progress))
        }
    }

    override fun applyInformationWithProgress() {
        super.applyInformationWithProgress()
        file.project.service<ArendMessagesService>().update(module)
        if (!ApplicationManager.getApplication().isUnitTestMode) {
            DaemonCodeAnalyzer.getInstance(myProject).restart()
        }
    }

    /*  TODO[server2]
        override fun applyInformationWithProgress() {
            println("applyInformationWithProgress: ${Thread.currentThread()}")
            if (file is ArendFile) myProject.service<ErrorService>().clearNameResolverErrors(file)
            super.applyInformationWithProgress()
            if (file !is ArendFile) return

            if (collector1.isEmpty && collector2.isEmpty) {
                return
            }

            val typeChecker = BackgroundTypechecker(myProject, instanceProviderSet, concreteProvider,
                maxOf(lastDefinitionModification, psiListenerService.definitionModificationTracker.modificationCount))
            if (ApplicationManager.getApplication().isUnitTestMode) {
                // DaemonCodeAnalyzer.restart does not work in tests
                typeChecker.runTypechecker(file, lastModifiedDefinition, collector1, collector2, false)
            } else {
                service<TypecheckingTaskQueue>().addTask(lastDefinitionModification) {
                    typeChecker.runTypechecker(file, lastModifiedDefinition, collector1, collector2, true)
                }
            }
        }
        */
}