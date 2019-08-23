package org.arend.highlight

import com.intellij.codeInsight.daemon.impl.HighlightInfoProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import org.arend.psi.ArendFile
import org.arend.toolWindow.errors.ArendMessagesView
import org.arend.typechecking.error.ErrorService

class TypecheckerPass(file: ArendFile, editor: Editor, highlightInfoProcessor: HighlightInfoProcessor)
    : BasePass(file, editor, "Arend typechecker annotator", TextRange(0, editor.document.textLength), highlightInfoProcessor) {

    override fun collectInformationWithProgress(progress: ProgressIndicator) {
        val errors = ErrorService.getInstance(myProject).getTypecheckingErrors(file)
        setProgressLimit(errors.size.toLong())
        for (pair in errors) {
            progress.checkCanceled()
            reportToEditor(pair.first, pair.second)
            advanceProgress(1)
        }
    }

    override fun applyInformationWithProgress() {
        super.applyInformationWithProgress()
        ErrorService.getInstance(myProject).updateTypecheckingErrors(file, null)
        ArendMessagesView.getInstance(myProject).update()
    }
}