package org.arend.actions

import com.intellij.codeInsight.daemon.impl.GotoNextErrorHandler
import com.intellij.codeInsight.daemon.impl.actions.GotoNextErrorAction
import com.intellij.codeInsight.daemon.impl.actions.GotoPreviousErrorAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.util.CommonProcessors
import org.arend.highlight.BasePass
import org.arend.psi.ArendFile
import org.arend.settings.ArendProjectSettings
import org.arend.toolWindow.errors.ArendMessagesService
import org.arend.toolWindow.errors.satisfies
import org.arend.typechecking.error.ErrorService

class ArendGotoNextErrorAction : GotoNextErrorAction() {
    override fun getHandler() = ArendGotoNextErrorHandler(true)
}

class ArendGotoPreviousErrorAction : GotoPreviousErrorAction() {
    override fun getHandler() = ArendGotoNextErrorHandler(false)
}

class ArendGotoNextErrorHandler(goForward: Boolean) : GotoNextErrorHandler(goForward) {
    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        super.invoke(project, editor, file)
        if (file is ArendFile) {
            selectErrorFromEditor(project, editor, file, always = true, activate = true)
        }
    }
}

fun selectErrorFromEditor(project: Project, editor: Editor, file: ArendFile?, always: Boolean, activate: Boolean) {
    val document = editor.document
    val offset = editor.caretModel.offset
    // Check that we are in a problem range
    if ((DocumentMarkupModel.forDocument(document, project, true) as? MarkupModelEx)?.processRangeHighlightersOverlappingWith(offset, offset + 1, CommonProcessors.alwaysFalse()) == true) {
        return
    }

    ApplicationManager.getApplication().run {
        executeOnPooledThread {
            var arendFile = file
            if (arendFile == null) {
                runReadAction {
                    arendFile = PsiDocumentManager.getInstance(project).getPsiFile(document) as? ArendFile
                }
            }
            if (arendFile == null) {
                return@executeOnPooledThread
            }
            runInEdt {
                val arendErrors = project.service<ErrorService>().getErrors(arendFile!!)
                if (arendErrors.isEmpty()) {
                    return@runInEdt
                }

                val service = project.service<ArendProjectSettings>()
                for (arendError in arendErrors) {
                    if (always || arendError.error.satisfies(service.autoScrollFromSource)) {
                        val textRange = BasePass.getImprovedTextRange(arendError.error) ?: continue
                        if (textRange.containsOffset(offset)) {
                            val messagesService = project.service<ArendMessagesService>()
                            messagesService.view?.tree?.select(arendError.error)
                            if (activate) {
                                messagesService.activate(project, false)
                            }
                            break
                        }
                    }
                }
            }
        }
    }
}
