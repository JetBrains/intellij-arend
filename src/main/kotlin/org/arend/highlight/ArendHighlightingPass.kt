package org.arend.highlight

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import org.arend.IArendFile
import org.arend.psi.*
import org.arend.server.ArendServerService
import org.arend.server.ProgressReporter
import org.arend.settings.ArendSettings
import org.arend.toolWindow.errors.ArendMessagesService
import org.arend.typechecking.*
import org.arend.typechecking.runner.RunnerService

class ArendHighlightingPass(file: IArendFile, editor: Editor, textRange: TextRange)
    : BasePass(file, editor, "Arend resolver annotator", textRange) {

    private val module = (file as? ArendFile)?.moduleLocation

    public override fun collectInformationWithProgress(progress: ProgressIndicator) {
        progress.isIndeterminate = true
        if (module == null) return

        val server = myProject.service<ArendServerService>().server
        server.getCheckerFor(listOf(module)).resolveModules(ProgressCancellationIndicator(progress), ProgressReporter.empty())
        val visitor = HighlightingVisitor(this, server.typingInfo)
        for (definitionData in server.getResolvedDefinitions(module)) {
            definitionData.definition.accept(visitor, null)
        }
    }

    override fun applyInformationWithProgress() {
        super.applyInformationWithProgress()
        myProject.service<ArendMessagesService>().update(module)

        if (module != null && service<ArendSettings>().isBackgroundTypechecking) {
            invokeLater {
                myProject.service<RunnerService>().runChecker(module)
            }
        } else if (!ApplicationManager.getApplication().isUnitTestMode) {
            DaemonCodeAnalyzer.getInstance(myProject).restart()
        }
    }
}