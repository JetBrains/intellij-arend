package org.arend.quickfix.referenceResolve

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.daemon.impl.DaemonListeners
import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInspection.HintAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiFile
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.ThreeState
import org.arend.naming.scope.ScopeFactory
import org.arend.psi.ArendFile
import org.arend.psi.ArendFileScope
import org.arend.psi.ext.*
import org.arend.psi.libraryConfig
import org.arend.psi.stubs.index.ArendDefinitionIndex
import org.arend.psi.stubs.index.ArendFileIndex
import org.arend.settings.ArendSettings
import org.arend.util.ArendBundle
import org.arend.util.FileUtils

enum class Result { POPUP_SHOWN, CLASS_AUTO_IMPORTED, POPUP_NOT_SHOWN }

class ArendImportHintAction(private val referenceElement: ArendReferenceElement) : HintAction, HighPriorityAction {
    private var availability: ImportHintActionAvailability = ImportHintActionAvailability.UNAVAILABLE
    private var itemsToImport: Sequence<ResolveReferenceAction> = emptySequence()

    init {
        val initializer = {
            val psiFile = referenceElement.containingFile
            val project = referenceElement.project
            availability = doComputeAvailability(project, referenceElement)
            itemsToImport = if (importQuickFixAllowed(referenceElement))
                getStubElementSet(project, referenceElement, psiFile).asSequence().mapNotNull {
                    ResolveReferenceAction.getProposedFix(it, referenceElement)
                }
            else emptySequence()
        }

        val application = ApplicationManager.getApplication()
        application.executeOnPooledThread{ runReadAction { initializer.invoke() } }
    }
    private enum class ImportHintActionAvailability { UNAVAILABLE, ONLY_AT_USER_REQUEST, AVAILABLE, AVAILABLE_FOR_SILENT_FIX }

    override fun startInWriteAction(): Boolean = false

    override fun getFamilyName(): String = text

    override fun getText(): String = ArendBundle.message("arend.import.fix")

    @Deprecated("Deprecated in Java")
    override fun fixSilently(editor: Editor): Boolean =
            doFix(editor, true) == Result.CLASS_AUTO_IMPORTED

    override fun showHint(editor: Editor): Boolean =
            doFix(editor) != Result.POPUP_NOT_SHOWN

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean =
        referenceElement.isValid && checkAvailability() != ImportHintActionAvailability.UNAVAILABLE

    private fun checkAvailability(): ImportHintActionAvailability {
        if (!importQuickFixAllowed(referenceElement)) return ImportHintActionAvailability.UNAVAILABLE
        return availability
    }

    private fun getItemsToImport(onlyGenerallyAvailable: Boolean = false): Sequence<ResolveReferenceAction> =
        itemsToImport.filter { !onlyGenerallyAvailable || !availableOnlyAtUserRequest(it.target) }


    override fun invoke(project: Project, editor: Editor, file: PsiFile?) {
        if (!FileModificationService.getInstance().prepareFileForWrite(file)) return
        if (!referenceUnresolved(referenceElement) && referenceElement.parent !is ArendPattern) return // already imported or invalid

        ApplicationManager.getApplication().runWriteAction {
            val fixData = getItemsToImport()
            if (!fixData.iterator().hasNext()) return@runWriteAction
            val action = ArendAddImportAction(project, editor, referenceElement, fixData.toList(), false)
            action.execute()
        }

    }

    private fun doFix(editor: Editor, silentFixMode: Boolean = false): Result {
        val psiFile = referenceElement.containingFile
        val refElementUnderCaret = referenceElement.textRange.contains(editor.caretModel.offset)
        val project = psiFile.project

        if (!referenceElement.isValid || referenceElement.reference?.resolve() != null) return Result.POPUP_NOT_SHOWN // already imported or invalid
        val availability = checkAvailability()
        if (availability !in setOf(ImportHintActionAvailability.AVAILABLE, ImportHintActionAvailability.AVAILABLE_FOR_SILENT_FIX)) return Result.POPUP_NOT_SHOWN // We import fieldDefIdentifier only at the request of the user (through invoke method)

        val isInModlessContext = if (Registry.`is`("ide.perProjectModality")) !LaterInvocator.isInModalContextForProject(editor.project) else !LaterInvocator.isInModalContext()
        val referenceResolveActions = getItemsToImport(true)
        val actionsIterator = referenceResolveActions.iterator()

        if (availability == ImportHintActionAvailability.AVAILABLE_FOR_SILENT_FIX &&
                service<ArendSettings>().autoImportOnTheFly && !refElementUnderCaret /* prevent on-the-fly autoimport of element under caret */ &&
                (ApplicationManager.getApplication().isUnitTestMode || DaemonListeners.canChangeFileSilently(psiFile, true, ThreeState.UNSURE)) &&
                isInModlessContext) {
            val action = ArendAddImportAction(project, editor, referenceElement, referenceResolveActions.toList(), true)
            CommandProcessor.getInstance().runUndoTransparentAction { action.execute() }
            return Result.CLASS_AUTO_IMPORTED
        }

        if (silentFixMode) return Result.POPUP_NOT_SHOWN

        val firstAction = if (actionsIterator.hasNext()) actionsIterator.next() else null
        val moreThanOneActionAvailable = actionsIterator.hasNext()

        if (firstAction != null) {
            val hintText = ShowAutoImportPass.getMessage(moreThanOneActionAvailable, firstAction.toString())
            if (!ApplicationManager.getApplication().isUnitTestMode) {
                var endOffset = referenceElement.textRange.endOffset
                if (endOffset > editor.document.textLength) endOffset = editor.document.textLength //needed to prevent elusive IllegalArgumentException
                val action = ArendAddImportAction(project, editor, referenceElement, referenceResolveActions.toList(), false)
                HintManager.getInstance().showQuestionHint(editor, hintText, referenceElement.textRange.startOffset, endOffset, action)
            }
            return Result.POPUP_SHOWN
        }
        return Result.POPUP_NOT_SHOWN
    }

    companion object {
        private fun availableOnlyAtUserRequest(referable: PsiLocatedReferable): Boolean =
                referable is ArendFieldDefIdentifier

        private fun doComputeAvailability(project: Project, refElement: ArendReferenceElement) = CachedValuesManager.getCachedValue(refElement) {
            val allStubs = getStubElementSet(project, refElement, refElement.containingFile).asSequence()
            val generallyAvailableStubs = allStubs.filter { !availableOnlyAtUserRequest(it) }
            val allImportActions = allStubs.filter { kindMatches(it, refElement) && ResolveReferenceAction.checkIfAvailable(it, refElement) }
            val generallyAvailableImportActions = generallyAvailableStubs.filter { kindMatches(it, refElement) && ResolveReferenceAction.checkIfAvailable(it, refElement) }
            CachedValueProvider.Result(when {
                generallyAvailableImportActions.iterator().hasNext() -> {
                    val allImportActionsIterator = allImportActions.iterator()
                    allImportActionsIterator.next()
                    if (allImportActionsIterator.hasNext()) ImportHintActionAvailability.AVAILABLE else ImportHintActionAvailability.AVAILABLE_FOR_SILENT_FIX
                }
                allImportActions.iterator().hasNext() -> ImportHintActionAvailability.ONLY_AT_USER_REQUEST
                else -> ImportHintActionAvailability.UNAVAILABLE
            }, PsiModificationTracker.MODIFICATION_COUNT)
        }

        private fun kindMatches(target: PsiLocatedReferable, element: ArendReferenceElement) : Boolean =
            element.parent !is ArendPattern || target.tcReferable?.kind?.isConstructor == true

        private fun getStubElementSet(project: Project, refElement: ArendReferenceElement, file: PsiFile?): List<PsiLocatedReferable> {
            val name = refElement.referenceName
            val config = (file as? ArendFile)?.libraryConfig
            val libRefs = emptyList<PsiLocatedReferable>() /* TODO[server2]: if (config == null) emptyList() else {
                val result = ArrayList<PsiLocatedReferable>()
                config.forAvailableConfigs { conf ->
                    conf.additionalNames[name]?.let { result.addAll(it) }
                    null
                }
                result
            } */
            return StubIndex.getElements(ArendDefinitionIndex.KEY, name, project, ArendFileScope(project), PsiReferable::class.java).filterIsInstance<PsiLocatedReferable>() +
                    StubIndex.getElements(ArendFileIndex.KEY, name + FileUtils.EXTENSION, project, ArendFileScope(project), ArendFile::class.java) +
                    libRefs // TODO[server2]: + project.service<TypeCheckingService>().getAdditionalReferables(name)
        }

        fun importQuickFixAllowed(referenceElement: ArendReferenceElement) = when (referenceElement) {
            is ArendDefIdentifier -> referenceElement.parent is ArendPattern
            is ArendRefIdentifier -> (referenceUnresolved(referenceElement) && ScopeFactory.isGlobalScopeVisible(referenceElement.topmostEquivalentSourceNode))
            is ArendSourceNode -> referenceUnresolved(referenceElement) && ScopeFactory.isGlobalScopeVisible(referenceElement.topmostEquivalentSourceNode)
            is ArendIPName -> referenceUnresolved(referenceElement)
            else -> false
        }

        fun referenceUnresolved(referenceElement: ArendReferenceElement): Boolean {
            val reference = (if (referenceElement.isValid) referenceElement.reference else null)
                    ?: return false // reference anchor is invalid
            return reference.resolve() == null // return false if already imported
        }
    }
}
