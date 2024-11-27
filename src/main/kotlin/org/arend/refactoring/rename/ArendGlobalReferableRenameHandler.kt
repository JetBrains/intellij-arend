package org.arend.refactoring.rename

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.startOffset
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.rename.*
import com.intellij.refactoring.rename.inplace.InplaceRefactoring
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer
import com.intellij.refactoring.util.TextOccurrencesUtil
import com.intellij.usageView.UsageInfo
import org.arend.intention.checkNotGeneratePreview
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.RedirectingReferable
import org.arend.naming.reference.RedirectingReferableImpl
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.ext.*
import org.arend.psi.getArendNameText
import org.arend.refactoring.rename.ArendGlobalReferableRenameHandler.Util.isMoreSpecific
import org.arend.resolving.ArendResolveCache
import java.awt.EventQueue.invokeLater

class ArendGlobalReferableRenameHandler : MemberInplaceRenameHandler() {
    override fun createMemberRenamer(element: PsiElement, elementToRename: PsiNameIdentifierOwner, editor: Editor): MemberInplaceRenamer {
        //Notice that element == elementToRename since currently there are no such things as inherited methods in Arend
        val project = editor.project
        if (project != null && (elementToRename is GlobalReferable || Util.isDefIdentifierFromNsId(elementToRename))) {
            val context = Util.getContext(project, elementToRename, editor)
            if (context != null) return ArendInplaceRenamer(elementToRename, editor, context)
        }

        return super.createMemberRenamer(element, elementToRename, editor)
    }

    override fun isAvailable(element: PsiElement?, editor: Editor, file: PsiFile): Boolean {
        val nameSuggestionContext = Util.findElementAtCaret(file, editor)
        var e = element
        if (e == null && LookupManager.getActiveLookup(editor) != null) {
            e = PsiTreeUtil.getParentOfType(nameSuggestionContext, PsiNamedElement::class.java)
        }
        return e is GlobalReferable || e is ArendAliasIdentifier || (e != null && Util.isDefIdentifierFromNsId(e))
    }

    override fun doRename(elementToRename: PsiElement, editor: Editor, dataContext: DataContext?): InplaceRefactoring? {
        if (ApplicationManager.getApplication().isUnitTestMode && dataContext != null) { //Invoked only in tests
            val newName = PsiElementRenameHandler.DEFAULT_NAME.getData(dataContext)
            if (newName != null) {
                val project = editor.project
                if (project != null) {
                    val context = Util.getContext(project, elementToRename, editor)
                    if (context != null) ArendRenameProcessor(project, elementToRename, newName, context, null).run()
                }
                return null
            }
        }
        if (elementToRename is PsiNameIdentifierOwner && (elementToRename is GlobalReferable || Util.isDefIdentifierFromNsId(
                elementToRename
            ))) {
            val renamer = createMemberRenamer(elementToRename, elementToRename, editor)
            val startedRename = renamer.performInplaceRename()
            if (!startedRename) {
                if (checkNotGeneratePreview()) {
                    invokeLater {
                        Util.customPerformDialogRename(elementToRename, editor)
                    }
                }
            }
            return null
        }
        return super.doRename(elementToRename, editor, dataContext)
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) { //handles both dialog/inplace/test renames
        val elementAtCaret = Util.findElementAtCaret(file, editor)
        val isIDinAlias = elementAtCaret is LeafPsiElement && elementAtCaret.elementType == ID && elementAtCaret.psi.parent is ArendAliasIdentifier
        if (isIDinAlias) {
            val globalReferable = (elementAtCaret as? LeafPsiElement)?.psi?.parent?.parent?.parent as? GlobalReferable
            if (globalReferable is PsiElement) doRename(globalReferable, editor, dataContext)
        } else
            super.invoke(project, editor, file, dataContext)
    }

    object Util {
        fun isDefIdentifierFromNsId(element: PsiElement) = element is ArendDefIdentifier && (element.parent?.let { it is ArendNsId && it.parent is ArendNsUsing } ?: false)
        fun isMoreSpecific(a: ArendNsId, b: ArendNsId): Boolean? {
            val scopeA = a.defIdentifier?.useScope as? LocalSearchScope
            val scopeB = b.defIdentifier?.useScope as? LocalSearchScope
            val aInB = scopeB?.containsRange(a.containingFile, a.textRange)
            val bInA = scopeA?.containsRange(b.containingFile, b.textRange)
            if (aInB == null || bInA == null) return null
            if (aInB && !bInA) return true
            if (bInA && !aInB) return false
            if (aInB && bInA) return a.startOffset >= b.startOffset
            return null
        }

        fun customPerformDialogRename(elementToRename: PsiElement, editor: Editor) {
            val project = editor.project
            if (project != null && (elementToRename is ReferableBase<*> || isDefIdentifierFromNsId(elementToRename))) {
                val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
                val elementAtCaret = if (psiFile != null) findElementAtCaret(psiFile, editor) else null

                val context = getContext(project, elementToRename, editor)
                if (context != null) {
                    val nameInDialog = when (context.nameKind) {
                        ArendNameKind.ALIAS_NAME -> (elementToRename as? ReferableBase<*>)?.alias?.aliasIdentifier?.text
                        ArendNameKind.NORMAL_NAME -> (elementToRename as? ReferableBase<*>)?.defIdentifier?.name
                        ArendNameKind.NSID_NAME -> elementAtCaret?.text
                    } ?: "???"

                    invokeLater {
                        val dialog = object: RenameDialog(project, elementToRename, elementToRename, editor) {
                            override fun getSuggestedNames(): Array<String> = arrayOf(nameInDialog)

                            override fun createRenameProcessor(newName: String): RenameProcessor =
                                ArendRenameProcessor(project, elementToRename, newName, context, null)
                        }
                        dialog.show()
                    }
                }
            }
        }

        fun findElementAtCaret(file: PsiFile, editor: Editor): PsiElement? {
            var caretElement: PsiElement?
            val offset = editor.caretModel.offset
            caretElement = file.findElementAt(offset)
            if (caretElement == null || caretElement is PsiWhiteSpace || caretElement is PsiComment || caretElement.elementType == COMMA || caretElement.elementType == DOT || caretElement.elementType == RPAREN || caretElement.elementType == RBRACE) caretElement = file.findElementAt(offset - 1)
            return caretElement
        }

        fun getContext(project: Project, elementToRename: PsiElement, editor: Editor): ArendRenameRefactoringContext? {
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
            val caretElement = if (psiFile != null) findElementAtCaret(psiFile, editor) else null
            val caretElementText = getArendNameText(caretElement)
            val redirectingReferable = (caretElement?.parent as? ArendCompositeElement)?.scope?.elements?.filterIsInstance<RedirectingReferable>()
                ?.firstOrNull { it.refName == caretElementText }

            if (elementToRename is GlobalReferable) {
                if (caretElement != null) {
                    val nameUnderCaret = when {
                        redirectingReferable is RedirectingReferableImpl -> ArendNameKind.NSID_NAME // e.g. name coming from a NsId operator in a namespace command
                        caretElementText == elementToRename.aliasName -> ArendNameKind.ALIAS_NAME
                        caretElementText == elementToRename.refName -> ArendNameKind.NORMAL_NAME
                        else -> throw IllegalStateException()
                    }
                    return ArendRenameRefactoringContext(caretElementText ?: return null, nameUnderCaret, editor.caretModel.offset, psiFile)
                }
            } else if (isDefIdentifierFromNsId(elementToRename) && psiFile != null) {
                return ArendRenameRefactoringContext(caretElementText ?: return null, ArendNameKind.NSID_NAME, editor.caretModel.offset, psiFile)
            }
            return null
        }
    }
}

enum class ArendNameKind{ NORMAL_NAME, ALIAS_NAME, NSID_NAME }
data class ArendRenameRefactoringContext(val caretElementText: String, val nameKind: ArendNameKind, val offset: Int, val file: PsiFile?) {
    constructor(name: String) : this (name, ArendNameKind.NORMAL_NAME, 0, null) // two last parameters will be unused anyway
}

class ArendInplaceRenamer(elementToRename: PsiNamedElement,
                          editor: Editor,
                          val context: ArendRenameRefactoringContext) :
        MemberInplaceRenamer(elementToRename, null, editor, context.caretElementText, context.caretElementText) {

    override fun collectRefs(referencesSearchScope: SearchScope?): MutableCollection<PsiReference> {
        var collection = super.collectRefs(referencesSearchScope)
        val caretPosition = myEditor.caretModel.offset
        val file = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.document)

        val elementToRename = myElementToRename
        if (context.nameKind == ArendNameKind.ALIAS_NAME && elementToRename is ReferableBase<*>) {
            val alias = elementToRename.alias
            val aliasIdentifier = alias?.aliasIdentifier
            if (aliasIdentifier != null) collection.add(object: PsiReferenceBase<ArendAlias>(alias, aliasIdentifier.textRangeInParent) {
                override fun resolve() = elementToRename
            })
        }

        val relevantNsId = calculateRelevantNsId(caretPosition, myOldName, file, collection)
        if (relevantNsId != null) collection = collection.filter { shouldBeIncluded(it, relevantNsId, context.nameKind) }

        if (context.nameKind == ArendNameKind.NSID_NAME) {
            relevantNsId?.defIdentifier?.let { collection.add(it.reference) }
        }

        return collection.filter { ref ->
            val textRange = getRangeToRename(ref)
            Comparing.strEqual(ref.element.text.substring(textRange.startOffset, textRange.endOffset), myOldName)
        }.toMutableList()
    }

    override fun acceptReference(reference: PsiReference?): Boolean = true

    override fun getNameIdentifier(): PsiElement? =
            if (context.nameKind != ArendNameKind.NORMAL_NAME) null else super.getNameIdentifier()

    override fun createInplaceRenamerToRestart(variable: PsiNamedElement, editor: Editor, initialName: String?): VariableInplaceRenamer =
            ArendInplaceRenamer(variable, editor, context)

    override fun performInplaceRename(): Boolean {
        if (!myEditor.settings.isVariableInplaceRenameEnabled) return false // initiate dialog rename
        return super.performInplaceRename()
    }

    override fun startsOnTheSameElement(handler: RefactoringActionHandler?, element: PsiElement?): Boolean {
        variable.let { v -> if (v is ReferableBase<*> && v.alias?.aliasIdentifier == element && element != null) return true }
        return super.startsOnTheSameElement(handler, element)
    }

    override fun createRenameProcessor(element: PsiElement, newName: String): RenameProcessor =
            ArendRenameProcessor(myProject, element, newName, context) { restoreCaretOffsetAfterRename() }
}

class ArendRenameProcessor(project: Project,
                           val element: PsiElement,
                           newName: String,
                           val context: ArendRenameRefactoringContext,
                           val restoreCaretCallBack: Runnable?) :
        RenameProcessor(project, element, newName,
                RenamePsiElementProcessor.forElement(element).isToSearchInComments(element),
                RenamePsiElementProcessor.forElement(element).isToSearchForTextOccurrences(element)
                        && TextOccurrencesUtil.isSearchTextOccurrencesEnabled(element)) {
    var relevantNsId: ArendNsId? = null

    override fun findUsages(): Array<UsageInfo> {
        myProject.service<ArendResolveCache>().clear()
        var collection: List<UsageInfo> = super.findUsages().toMutableList()

        val nsId = calculateRelevantNsId(context.offset, context.caretElementText, context.file, collection.map { it.reference })
        if (nsId != null) collection = collection.filter { usageInfo -> usageInfo.reference?.let { shouldBeIncluded(it, nsId, context.nameKind) } ?: false }
        relevantNsId = nsId

        return collection.filter {
            getArendNameText(it.element) == context.caretElementText
        }.toTypedArray()
    }

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        val oldRefName = (element as? GlobalReferable)?.refName
        val newName = getNewName(element)
        super.performRefactoring(usages)
        if (context.nameKind == ArendNameKind.ALIAS_NAME) {
            if (oldRefName != null) (element as? PsiNamedElement)?.setName(oldRefName) // restore old refName
            (element as? ReferableBase<*>)?.alias?.aliasIdentifier?.setName(newName)
        } else if (context.nameKind == ArendNameKind.NSID_NAME) {
            if (oldRefName != null) (element as? PsiNamedElement)?.setName(oldRefName)
            relevantNsId?.defIdentifier?.setName(newName)
        }
        restoreCaretCallBack?.run()
    }
}

fun calculateRelevantNsId(caretPosition: Int, myOldName: String, file: PsiFile?, collection: Collection<PsiReference?>): ArendNsId? {
    var relevantNsId: ArendNsId? = null
    for (ref in collection) {
        val element = ref?.element ?: continue
        (element.parent as? ArendNsId)?.let {
            val scope = it.defIdentifier?.useScope as? LocalSearchScope
            if (file != null && scope?.containsRange(file, TextRange(caretPosition, caretPosition)) == true && (it.defIdentifier?.text == myOldName) &&
                (relevantNsId?.let{ rNI -> isMoreSpecific(it, rNI) == true} != false)) relevantNsId = it
        }
    }
    return relevantNsId
}

fun shouldBeIncluded(ref: PsiReference, relevantNsId: ArendNsId, kind: ArendNameKind): Boolean {
    val element = ref.element
    val isRefInsideRelevantNsId = element.parent == relevantNsId
    val isUsageInsideRelevantNsIdScope = (relevantNsId.defIdentifier?.useScope as? LocalSearchScope)?.containsRange(element.containingFile, element.textRange) == true
    val decision = when (kind) {
        ArendNameKind.NSID_NAME -> !isRefInsideRelevantNsId && isUsageInsideRelevantNsIdScope
        else -> isRefInsideRelevantNsId || !isUsageInsideRelevantNsIdScope
    }
    return decision
}