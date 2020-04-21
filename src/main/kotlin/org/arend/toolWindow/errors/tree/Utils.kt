package org.arend.toolWindow.errors.tree

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.CommonProcessors
import org.arend.highlight.BasePass
import org.arend.psi.ArendFile
import org.arend.settings.ArendProjectSettings
import org.arend.toolWindow.errors.satisfies
import org.arend.typechecking.error.ArendError
import org.arend.typechecking.error.ErrorService

fun selectedArendError(editor: Editor) = editor.project?.let { selectedArendErrors(it, editor).firstOrNull() }

fun selectedArendError(project: Project, editor: Editor) = selectedArendErrors(project, editor).firstOrNull()

fun selectedArendErrors(project: Project, editor: Editor): Sequence<ArendError> {
    val document = editor.document
    val offset = editor.caretModel.offset
    // Check that we are in a problem range
    if ((DocumentMarkupModel.forDocument(document, project, true) as? MarkupModelEx)?.processRangeHighlightersOverlappingWith(offset, offset, CommonProcessors.alwaysFalse()) == true)
        return emptySequence()
    val file = PsiDocumentManager.getInstance(project).getPsiFile(document) as? ArendFile
            ?: return emptySequence()
    val arendErrors = project.service<ErrorService>().getErrors(file)
    val service = project.service<ArendProjectSettings>()
    return arendErrors.asSequence()
            .filter { arendError -> arendError.error.satisfies(service.autoScrollFromSource) }
            .filter { arendError -> BasePass.getImprovedTextRange(arendError.error)?.contains(offset) == true }
}
