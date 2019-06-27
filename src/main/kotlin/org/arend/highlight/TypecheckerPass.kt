package org.arend.highlight

import com.intellij.codeInsight.daemon.impl.*
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import org.arend.psi.ArendFile
import org.arend.typechecking.TypeCheckingService

class TypecheckerPass(private val file: ArendFile, editor: Editor, textRange: TextRange, highlightInfoProcessor: HighlightInfoProcessor)
    : ProgressableTextEditorHighlightingPass(file.project, editor.document, "Arend typechecker annotator", file, editor, textRange, false, highlightInfoProcessor) {

    private val holder = AnnotationHolderImpl(AnnotationSession(file))

    override fun getDocument(): Document = super.getDocument()!!

    override fun collectInformationWithProgress(progress: ProgressIndicator) {
        val errors = TypeCheckingService.getInstance(file.project).getErrors(file)
        setProgressLimit(errors.size.toLong())
        for (pair in errors) {
            progress.checkCanceled()
            BasePass.processError(pair.first, pair.second, holder)
            advanceProgress(1)
        }
    }

    override fun applyInformationWithProgress() {
        val highlights = holder.map { HighlightInfo.fromAnnotation(it) }
        ApplicationManager.getApplication().invokeLater({
            if (isValid) {
                UpdateHighlightersUtil.setHighlightersToEditor(file.project, document, 0, document.textLength, highlights, colorsScheme, id)
            }
        }, ModalityState.stateForComponent(editor.component))
    }
}