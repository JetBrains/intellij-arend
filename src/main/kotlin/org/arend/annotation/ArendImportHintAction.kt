package org.arend.annotation

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
import com.intellij.psi.search.ProjectAndLibrariesScope
import com.intellij.psi.stubs.StubIndex
import org.arend.naming.reference.Referable
import org.arend.naming.scope.ScopeFactory
import org.arend.prelude.Prelude
import org.arend.term.group.Group
import org.arend.module.ArendPreludeLibrary
import org.arend.psi.ArendFieldDefIdentifier
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.PsiReferable
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.ArendSourceNode
import org.arend.psi.stubs.index.ArendDefinitionIndex
import org.arend.quickfix.ResolveRefFixData
import org.arend.quickfix.ResolveRefQuickFix
import org.arend.typechecking.TypeCheckingService

enum class Result {POPUP_SHOWN, CLASS_AUTO_IMPORTED, POPUP_NOT_SHOWN}

class ArendImportHintAction(private val referenceElement: ArendReferenceElement) : HintAction, HighPriorityAction {

    override fun startInWriteAction(): Boolean = false

    override fun getFamilyName(): String = "arend.reference.resolve"

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
            val name = referenceElement.referenceName

            val libraryManager = TypeCheckingService.getInstance(project).libraryManager
            val preludeLibrary = libraryManager.getRegisteredLibrary("prelude")
            val preludeItems = HashSet<Referable>()
            if (preludeLibrary is ArendPreludeLibrary) {
                val moduleGroup = preludeLibrary.getModuleGroup(Prelude.MODULE_PATH)
                if (moduleGroup != null) {
                    iterateOverGroup(moduleGroup, { referable: Referable ->
                        referable is PsiLocatedReferable && referable.name == referenceElement.referenceName
                    }, preludeItems)
                }
            }

            val items = StubIndex.getElements(ArendDefinitionIndex.KEY, name, project, ProjectAndLibrariesScope(project), PsiReferable::class.java).filterIsInstance<PsiLocatedReferable>().
                    union(preludeItems.filterIsInstance(PsiLocatedReferable::class.java))

            return items.mapNotNull { ResolveRefQuickFix.getDecision(it, referenceElement) }
        }

        return emptyList()
    }

    override fun getText(): String {
        return "Fix import"
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile?) {
        if (!FileModificationService.getInstance().prepareFileForWrite(file)) return

        if (!referenceUnresolved(referenceElement)) return // already imported or invalid

        ApplicationManager.getApplication().runWriteAction {
            val fixData = getItemsToImport()
            if (fixData.isEmpty()) return@runWriteAction
            val action = ArendAddImportAction(project, editor, referenceElement, fixData)
            action.execute()
        }

    }

    fun doFix(editor: Editor, allowPopup : Boolean) : Result {
        if (!referenceElement.isValid || referenceElement.reference?.resolve() != null) return Result.POPUP_NOT_SHOWN // already imported or invalid
        val fixData = getItemsToImport()
        val filteredFixData = fixData.filter { it.target !is ArendFieldDefIdentifier }

        if (fixData.isEmpty()) return Result.POPUP_NOT_SHOWN // already imported

        val psiFile = referenceElement.containingFile
        val project = referenceElement.project

        val action = ArendAddImportAction(project, editor, referenceElement, fixData)
        val isInModlessContext = if (Registry.`is`("ide.perProjectModality"))
            !LaterInvocator.isInModalContextForProject(editor.project)
        else
            !LaterInvocator.isInModalContext()

        if (fixData.size == 1 && filteredFixData.size == 1 // thus we prevent autoimporting short class field names
                && CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY &&
                (ApplicationManager.getApplication().isUnitTestMode || DaemonListeners.canChangeFileSilently(psiFile)) && isInModlessContext) {
            CommandProcessor.getInstance().runUndoTransparentAction { action.execute() }
            return Result.CLASS_AUTO_IMPORTED
        }

        if (allowPopup && filteredFixData.isNotEmpty()) { // thus we prevent showing hint-action for class field names
            val hintText = ShowAutoImportPass.getMessage(fixData.size > 1, fixData[0].toString())
            if (!ApplicationManager.getApplication().isUnitTestMode) {
                var endOffset = referenceElement.textRange.endOffset
                if (endOffset > editor.document.textLength) endOffset = editor.document.textLength //needed to prevent elusive IllegalArgumentException
                HintManager.getInstance().showQuestionHint(editor, hintText, referenceElement.textRange.startOffset, endOffset, action)
            }
            return Result.POPUP_SHOWN
        }
        return Result.POPUP_NOT_SHOWN
    }

    companion object {
        fun importQuickFixAllowed(referenceElement: ArendReferenceElement) =
            referenceElement is ArendSourceNode && referenceUnresolved(referenceElement) && ScopeFactory.isGlobalScopeVisible(referenceElement.topmostEquivalentSourceNode)

        fun referenceUnresolved(referenceElement: ArendReferenceElement): Boolean {
            val reference = (if (referenceElement.isValid) referenceElement.reference else null) ?: return false // reference element is invalid
            return reference.resolve() == null // return false if already imported
        }

        fun getResolved(referenceElement: ArendReferenceElement): PsiElement? =
            if (referenceElement.isValid) referenceElement.reference?.resolve() else null

        fun iterateOverGroup(group: Group, predicate: (Referable) -> Boolean, target: MutableSet<Referable>) {
            for (sg in group.subgroups) {
                if (sg is Referable && predicate.invoke(sg)) target.add(sg)
                iterateOverGroup(sg, predicate, target)
            }
        }
    }
}
