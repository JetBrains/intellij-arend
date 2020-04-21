package org.arend.highlight

import com.intellij.codeInsight.daemon.impl.HighlightInfoProcessor
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import org.arend.psi.ArendFile
import org.arend.toolWindow.errors.ArendMessagesService
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.error.local.GoalError

class TypecheckerPass(file: ArendFile, editor: Editor, highlightInfoProcessor: HighlightInfoProcessor)
    : BasePass(file, editor, "Arend typechecker annotator", TextRange(0, editor.document.textLength), highlightInfoProcessor) {

    override fun collectInformationWithProgress(progress: ProgressIndicator) {
        val errors = myProject.service<ErrorService>().getTypecheckingErrors(file)
        setProgressLimit(errors.size.toLong())
        for (pair in errors) {
            progress.checkCanceled()
            if (pair.second.isValid) {
                val error = pair.first
                reportToEditor(error, pair.second)
                if (error is GoalError) {
                    for (embeddedError in error.errors) {
                        getCauseElement(embeddedError.cause)?.let {
                            reportToEditor(embeddedError, it)
                        }
                    }
                }
            }
            advanceProgress(1)
        }
    }

    override fun applyInformationWithProgress() {
        super.applyInformationWithProgress()
        myProject.service<ErrorService>().updateTypecheckingErrors(file, null)
        myProject.service<ArendMessagesService>().update()
    }
}