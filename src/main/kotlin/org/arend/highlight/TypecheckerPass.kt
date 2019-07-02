package org.arend.highlight

import com.intellij.codeInsight.daemon.impl.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import org.arend.psi.ArendFile
import org.arend.typechecking.TypeCheckingService

class TypecheckerPass(file: ArendFile, editor: Editor, highlightInfoProcessor: HighlightInfoProcessor)
    : BasePass(file, editor, "Arend typechecker annotator", TextRange(0, editor.document.textLength), highlightInfoProcessor) {

    override fun collectInformationWithProgress(progress: ProgressIndicator) {
        val errors = TypeCheckingService.getInstance(myProject).getErrors(file)
        setProgressLimit(errors.size.toLong())
        for (pair in errors) {
            progress.checkCanceled()
            report(pair.first, pair.second)
            advanceProgress(1)
        }
    }
}