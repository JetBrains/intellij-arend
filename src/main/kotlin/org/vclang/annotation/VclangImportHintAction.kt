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
import com.intellij.psi.PsiFile
import com.intellij.psi.search.ProjectAndLibrariesScope
import com.intellij.psi.stubs.StubIndex
import org.vclang.psi.VcLongName
import org.vclang.psi.VcNsId
import org.vclang.psi.ext.PsiLocatedReferable
import org.vclang.psi.ext.PsiReferable
import org.vclang.psi.ext.VcReferenceElement
import org.vclang.psi.stubs.index.VcDefinitionIndex
import org.vclang.quickfix.ResolveRefFixData
import org.vclang.quickfix.ResolveRefQuickFix

enum class Result {POPUP_SHOWN, CLASS_AUTO_IMPORTED, POPUP_NOT_SHOWN}

class VclangImportHintAction(private val referenceElement: VcReferenceElement) : HintAction, HighPriorityAction {

    override fun startInWriteAction(): Boolean = false

    override fun getFamilyName(): String = "vclang.reference.resolve"

    override fun showHint(editor: Editor): Boolean {
        val result = doFix(editor, true)
        return result == Result.POPUP_SHOWN || result == Result.CLASS_AUTO_IMPORTED
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return this.getItemsToImport().isNotEmpty()
    }

    private fun getItemsToImport() : List<ResolveRefFixData> {
        if (importQuickFixAllowed(referenceElement)) {
            val project = referenceElement.project
            val indexedDefinitions : List<PsiLocatedReferable> = StubIndex.getElements(VcDefinitionIndex.KEY, referenceElement.referenceName, project, ProjectAndLibrariesScope(project), PsiReferable::class.java).filterIsInstance<PsiLocatedReferable>()

            return indexedDefinitions.mapNotNull { ResolveRefQuickFix.getDecision(it, referenceElement) }
        }

        return emptyList()
    }

    override fun getText(): String {
        return "Fix import"
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (!FileModificationService.getInstance().prepareFileForWrite(file)) return

        if (!referenceUnresolved(referenceElement)) return // already imported or invalid

        ApplicationManager.getApplication().runWriteAction {
            val fixData = getItemsToImport()
            if (fixData.isEmpty()) return@runWriteAction
            val action = VclangAddImportAction(project, editor!!, referenceElement, fixData)
            action.execute()
        }

    }

    fun doFix(editor: Editor, allowPopup : Boolean) : Result {
        if (!referenceElement.isValid || referenceElement.reference?.resolve() != null) return Result.POPUP_NOT_SHOWN // already imported or invalid
        val fixData = getItemsToImport()
        if (fixData.isEmpty()) return Result.POPUP_NOT_SHOWN // already imported

        val psiFile = referenceElement.containingFile
        val project = referenceElement.project

        val action = VclangAddImportAction(project, editor, referenceElement, fixData)
        val isInModlessContext = if (Registry.`is`("ide.perProjectModality"))
            !LaterInvocator.isInModalContextForProject(editor.project)
        else
            !LaterInvocator.isInModalContext()

        if (fixData.size == 1 && CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY &&
                (ApplicationManager.getApplication().isUnitTestMode || DaemonListeners.canChangeFileSilently(psiFile)) && isInModlessContext) {
            CommandProcessor.getInstance().runUndoTransparentAction { action.execute() }
            return Result.CLASS_AUTO_IMPORTED
        }

        if (allowPopup) {
            val hintText = ShowAutoImportPass.getMessage(fixData.size > 1, fixData[0].toString())
            if (!ApplicationManager.getApplication().isUnitTestMode) {
                HintManager.getInstance().showQuestionHint(editor, hintText, referenceElement.textOffset, referenceElement.textRange.endOffset, action)
            }
            return Result.POPUP_SHOWN
        }
        return Result.POPUP_NOT_SHOWN
    }

    companion object {
        fun importQuickFixAllowed(referenceElement : VcReferenceElement) : Boolean {
            if (!referenceUnresolved(referenceElement)) return false

            val parent = referenceElement.parent
            if (parent is VcLongName && referenceElement.prevSibling != null) return false // we resolve only first identifiers in longNames
            if (parent is VcNsId) return false // do not resolve identifiers in using/hiding lists in namespace commands

            return true
        }

        fun referenceUnresolved(referenceElement : VcReferenceElement) : Boolean {
            val reference = referenceElement.reference

            if (!referenceElement.isValid || reference == null) return false //reference element is invalid
            val psiElement = reference.resolve()

            return psiElement == null // return false if already imported
        }
    }
}
