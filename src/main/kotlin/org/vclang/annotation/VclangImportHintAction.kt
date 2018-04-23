package org.vclang.annotation

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.daemon.impl.DaemonListeners
import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInspection.HintAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.vclang.quickfix.ResolveRefFixData

enum class Result {POPUP_SHOWN, CLASS_AUTO_IMPORTED, POPUP_NOT_SHOWN}

class VclangImportHintAction(val currentElement : PsiElement, val classesToImport: List<ResolveRefFixData>) : HintAction, HighPriorityAction {
    override fun startInWriteAction(): Boolean = false

    override fun getFamilyName(): String = "vclang.reference.resolve"

    override fun showHint(editor: Editor): Boolean {
        val result = doFix(editor, true)
        return result == Result.POPUP_SHOWN || result == Result.CLASS_AUTO_IMPORTED
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return this.classesToImport.isNotEmpty()
    }

    override fun getText(): String {
        return "Fix import"
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (!FileModificationService.getInstance().prepareFileForWrite(file)) return
        ApplicationManager.getApplication().runWriteAction {
            if (classesToImport.isEmpty()) return@runWriteAction
            val action = VclangAddImportAction(project, editor!!, currentElement, classesToImport)
            action.execute()
        }

    }

    fun doFix(editor: Editor, allowPopup : Boolean) : Result {
        if (classesToImport.isEmpty()) return Result.POPUP_NOT_SHOWN

        val psiFile = currentElement.containingFile
        val classes = classesToImport.toTypedArray()
        val project = currentElement.project

        val action = VclangAddImportAction(project, editor, currentElement, classesToImport)

        val canImportHere = true

        val isInModlessContext = if (Registry.`is`("ide.perProjectModality"))
            !LaterInvocator.isInModalContextForProject(editor.project)
        else
            !LaterInvocator.isInModalContext()

        if (classes.size == 1 &&
                CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY &&
                (ApplicationManager.getApplication().isUnitTestMode || DaemonListeners.canChangeFileSilently(psiFile)) &&
                isInModlessContext) {
            CommandProcessor.getInstance().runUndoTransparentAction { action.execute() }
            return Result.CLASS_AUTO_IMPORTED
        }

        if (allowPopup && canImportHere) {
            val hintText = ShowAutoImportPass.getMessage(classes.size > 1, currentElement.text)
            if (!ApplicationManager.getApplication().isUnitTestMode && !HintManager.getInstance().hasShownHintsThatWillHideByOtherHint(true)) {
                HintManager.getInstance().showQuestionHint(editor, hintText, currentElement.textOffset, currentElement.textRange.endOffset, action)
            }
            return Result.POPUP_SHOWN
        }
        return Result.POPUP_NOT_SHOWN
    }
}
