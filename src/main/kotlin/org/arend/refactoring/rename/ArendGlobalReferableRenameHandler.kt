package org.arend.refactoring.rename

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.rename.*
import com.intellij.refactoring.rename.inplace.InplaceRefactoring
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer
import com.intellij.refactoring.util.TextOccurrencesUtil
import com.intellij.usageView.UsageInfo
import org.arend.naming.reference.GlobalReferable
import org.arend.psi.ArendAlias
import org.arend.psi.ArendAliasIdentifier
import org.arend.psi.ArendElementTypes.ID
import org.arend.psi.ext.ArendAliasIdentifierImplMixin
import org.arend.psi.ext.impl.ReferableAdapter
import org.arend.psi.getArendNameText

class ArendGlobalReferableRenameHandler : MemberInplaceRenameHandler() {
    override fun createMemberRenamer(element: PsiElement, elementToRename: PsiNameIdentifierOwner, editor: Editor): MemberInplaceRenamer {
        //Notice that element == elementToRename since currently there are no such things as inherited methods in Arend
        val project = editor.project
        if (project != null && elementToRename is GlobalReferable && elementToRename.hasAlias()) {
            val context = getContext(project, elementToRename, editor)
            if (context != null) return ArendInplaceRenamer(elementToRename, editor, context.name, context.isAlias)
        }

        return super.createMemberRenamer(element, elementToRename, editor)
    }

    override fun isAvailable(element: PsiElement?, editor: Editor, file: PsiFile): Boolean {
        val nameSuggestionContext = findElementAtCaret(file, editor)
        var e = element
        if (e == null && LookupManager.getActiveLookup(editor) != null) {
            e = PsiTreeUtil.getParentOfType(nameSuggestionContext, PsiNamedElement::class.java)
        }
        return e is GlobalReferable || e is ArendAliasIdentifier
    }

    override fun doRename(elementToRename: PsiElement, editor: Editor, dataContext: DataContext?): InplaceRefactoring? {
        if (ApplicationManager.getApplication().isUnitTestMode && dataContext != null) { //Invoked only in tests
            val newName = PsiElementRenameHandler.DEFAULT_NAME.getData(dataContext)
            val project = editor.project
            if (project != null && newName != null && elementToRename is PsiNameIdentifierOwner) {
                val context = getContext(project, elementToRename, editor)
                if (context != null) ArendRenameProcessor(project, elementToRename, newName, context.name, context.isAlias).run()
            }
            return null
        }
        if (elementToRename is PsiNameIdentifierOwner && elementToRename is GlobalReferable) {
            val renamer = createMemberRenamer(elementToRename, elementToRename, editor)
            val startedRename = renamer.performInplaceRename()
            if (!startedRename)
                customPerformDialogRename(elementToRename, editor)
            return null
        }
        return super.doRename(elementToRename, editor, dataContext)
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) { //handles both dialog/inplace/test renames
        val elementAtCaret = findElementAtCaret(file, editor)
        val isIDinAlias = elementAtCaret is LeafPsiElement && elementAtCaret.elementType == ID && elementAtCaret.psi.parent is ArendAliasIdentifier
        if (isIDinAlias) {
            val globalReferable = (elementAtCaret as? LeafPsiElement)?.psi?.parent?.parent?.parent as? GlobalReferable
            if (globalReferable is PsiElement) doRename(globalReferable, editor, dataContext)
        } else
            super.invoke(project, editor, file, dataContext)
    }

    companion object {
        private fun customPerformDialogRename(elementToRename: PsiElement, editor: Editor) {
            val project = editor.project
            if (project != null && elementToRename is ReferableAdapter<*>) {
                val context = getContext(project, elementToRename, editor)
                if (context != null) {
                    val dialog = object: RenameDialog(project, elementToRename, if (context.isAlias) elementToRename.getAlias() else elementToRename, editor) {
                        override fun createRenameProcessor(newName: String): RenameProcessor {
                            return ArendRenameProcessor(project, elementToRename, newName, context.name, context.isAlias)
                        }
                    }
                    dialog.show()
                }
            }
        }

        fun findElementAtCaret(file: PsiFile, editor: Editor): PsiElement? {
            var caretElement: PsiElement?
            val offset = editor.caretModel.offset
            caretElement = file.findElementAt(offset)
            if (caretElement == null || caretElement is PsiWhiteSpace || caretElement is PsiComment) caretElement = file.findElementAt(offset - 1)
            return caretElement
        }

        fun getContext(project: Project, elementToRename: PsiNameIdentifierOwner, editor: Editor): ArendRenameRefactoringContext? {
            if (elementToRename is GlobalReferable && elementToRename.hasAlias()) {
                val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
                val caretElement = if (psiFile != null) findElementAtCaret(psiFile, editor) else null
                val caretElementText = getArendNameText(caretElement)

                if (caretElement != null) {
                    val aliasUnderCaret = when {
                        caretElementText == elementToRename.aliasName -> true
                        caretElementText == elementToRename.refName -> false
                        else -> null
                    }
                    if (aliasUnderCaret != null) {
                        val aliasName = elementToRename.aliasName
                        val name = if (aliasUnderCaret && aliasName != null) aliasName else elementToRename.refName
                        return ArendRenameRefactoringContext(name, aliasUnderCaret)
                    }
                }
            }
            return null
        }

        data class ArendRenameRefactoringContext(val name: String, val isAlias: Boolean)
    }
}

class ArendInplaceRenamer(elementToRename: PsiNamedElement,
                          editor: Editor,
                          val oldName: String,
                          val aliasUnderCaret: Boolean) :
        MemberInplaceRenamer(elementToRename, null, editor, oldName, oldName) {

    override fun collectRefs(referencesSearchScope: SearchScope?): MutableCollection<PsiReference> {
        val collection = super.collectRefs(referencesSearchScope)
        val element = myElementToRename
        if (aliasUnderCaret && element is ReferableAdapter<*>) {
            val alias = element.getAlias()
            if (alias?.aliasIdentifier != null) collection.add(MyReference(alias))
        }
        return collection
    }

    override fun getNameIdentifier(): PsiElement? {
        if (aliasUnderCaret) return null
        return super.getNameIdentifier()
    }

    override fun performInplaceRename(): Boolean {
        if (!myEditor.settings.isVariableInplaceRenameEnabled) return false // initiate dialog rename
        return super.performInplaceRename()
    }

    override fun startsOnTheSameElement(handler: RefactoringActionHandler?, element: PsiElement?): Boolean {
        variable.let { v -> if (v is ReferableAdapter<*> && v.getAlias()?.aliasIdentifier == element && element != null) return true }
        return super.startsOnTheSameElement(handler, element)
    }

    inner class MyReference(element: ArendAlias) : PsiReferenceBase<ArendAlias>(element, element.aliasIdentifier!!.textRangeInParent) {
        override fun resolve(): PsiElement? = element
    }

    override fun acceptReference(reference: PsiReference?): Boolean {
        if (reference != null) {
            val element = reference.element
            val textRange = getRangeToRename(reference)
            val referenceText = element.text.substring(textRange.startOffset, textRange.endOffset)
            return Comparing.strEqual(referenceText, myOldName)
        }
        return super.acceptReference(reference)
    }

    override fun createRenameProcessor(element: PsiElement, newName: String): RenameProcessor =
            ArendRenameProcessor(myProject, element, newName, oldName, aliasUnderCaret)
}

class ArendRenameProcessor(project: Project, val element: PsiElement, newName: String, val oldName: String, val isAlias: Boolean) :
        RenameProcessor(project, element, newName,
                RenamePsiElementProcessor.forElement(element).isToSearchInComments(element),
                RenamePsiElementProcessor.forElement(element).isToSearchForTextOccurrences(element)
                        && TextOccurrencesUtil.isSearchTextOccurrencesEnabled(element)) {
    override fun findUsages(): Array<UsageInfo> {
        val superUsages = super.findUsages()
        return superUsages.filter {
            getArendNameText(it.element) == oldName
        }.toTypedArray()
    }

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        val oldRefName = (element as? GlobalReferable)?.refName
        val newName = getNewName(element)
        super.performRefactoring(usages)
        if (isAlias) {
            if (oldRefName != null) (element as? PsiNamedElement)?.setName(oldRefName) // restore old refName
            ((element as? ReferableAdapter<*>)?.getAlias()?.aliasIdentifier as? ArendAliasIdentifierImplMixin)?.setName(newName)
        }
    }
}