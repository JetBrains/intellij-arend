package org.arend.refactoring

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer
import com.intellij.refactoring.util.TextOccurrencesUtil
import com.intellij.usageView.UsageInfo
import org.arend.naming.reference.GlobalReferable
import org.arend.psi.ArendAlias
import org.arend.psi.ArendElementTypes.ID
import org.arend.psi.ArendPsiFactory
import org.arend.psi.childOfType
import org.arend.psi.ext.impl.ReferableAdapter
import org.arend.psi.replaceWithNotification

class ArendInplaceMemberRenameHandler : MemberInplaceRenameHandler() {
    override fun createMemberRenamer(element: PsiElement, elementToRename: PsiNameIdentifierOwner, editor: Editor): MemberInplaceRenamer {
        val project = editor.project
        if (project != null && elementToRename is GlobalReferable && elementToRename.hasAlias()) {
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
            val caretElement = if (psiFile != null) findElementAtCaret(psiFile, editor) else null

            if (caretElement != null) {
                val aliasUnderCaret = when {
                    caretElement.text == elementToRename.aliasName -> true
                    caretElement.text == elementToRename.refName -> false
                    else -> null
                }
                if (aliasUnderCaret != null) {
                    val aliasName = elementToRename.aliasName
                    val name = if (aliasUnderCaret && aliasName != null) aliasName else elementToRename.refName
                    return ArendInplaceRenamer(elementToRename, element, editor, name, aliasUnderCaret)
                }
            }
        }

        return super.createMemberRenamer(element, elementToRename, editor)
    }

    override fun isAvailable(element: PsiElement?, editor: Editor, file: PsiFile): Boolean {
        val elementAtCaret = findElementAtCaret(file, editor)
        val isIDinAlias = elementAtCaret is LeafPsiElement && elementAtCaret.elementType == ID && elementAtCaret.psi.parent is ArendAlias
        if (isIDinAlias) return true
        return super.isAvailable(element, editor, file)
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        val elementAtCaret = findElementAtCaret(file, editor)
        val isIDinAlias = elementAtCaret is LeafPsiElement && elementAtCaret.elementType == ID && elementAtCaret.psi.parent is ArendAlias
        if (isIDinAlias) {
            val globalReferable = (elementAtCaret as? LeafPsiElement)?.psi?.parent?.parent as? GlobalReferable
            if (globalReferable is PsiElement) doRename(globalReferable, editor, dataContext)
        } else
            super.invoke(project, editor, file, dataContext)
    }

    companion object {
        fun findElementAtCaret(file: PsiFile, editor: Editor): PsiElement? {
            var caretElement: PsiElement? = null
            val offset = editor.caretModel.offset
            caretElement = file.findElementAt(offset)
            if (caretElement == null || caretElement is PsiWhiteSpace || caretElement is PsiComment) caretElement = file.findElementAt(offset-1)
            return caretElement
        }
    }
}

class ArendInplaceRenamer(elementToRename: PsiNamedElement,
                          substituted: PsiElement?,
                          editor: Editor,
                          name: String,
                          val aliasUnderCaret: Boolean) :
        MemberInplaceRenamer(elementToRename, substituted, editor, name, name) {

    override fun collectRefs(referencesSearchScope: SearchScope?): MutableCollection<PsiReference> {
        val collection = super.collectRefs(referencesSearchScope)
        val element = myElementToRename
        if (aliasUnderCaret && element is ReferableAdapter<*>) {
            val alias = element.getAlias()
            if (alias?.id != null) collection.add(MyReference(alias))
        }
        return collection
    }

    override fun getNameIdentifier(): PsiElement? {
        if (aliasUnderCaret) return null
        return super.getNameIdentifier()
    }

    inner class MyReference(element: ArendAlias) : PsiReferenceBase<ArendAlias>(element, element.id!!.textRangeInParent) {
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
            ArendRenameProcessor(element, newName)

    private inner class ArendRenameProcessor(val element: PsiElement, newName: String) :
            RenameProcessor(this@ArendInplaceRenamer.myProject, element, newName,
                    RenamePsiElementProcessor.forElement(element).isToSearchInComments(element),
                    RenamePsiElementProcessor.forElement(element).isToSearchForTextOccurrences(element)
                            && TextOccurrencesUtil.isSearchTextOccurrencesEnabled(element)) {
        override fun findUsages(): Array<UsageInfo> {
            val superUsages = super.findUsages()
            return superUsages.filter { it.element?.text == this@ArendInplaceRenamer.myOldName }.toTypedArray()
        }

        override fun performRefactoring(usages: Array<out UsageInfo>) {
            val oldRefName = (element as? GlobalReferable)?.refName
            val newName = getNewName(element)
            super.performRefactoring(usages)
            if (aliasUnderCaret) {
                if (oldRefName != null) (element as? PsiNamedElement)?.setName(oldRefName) // restore old refName
                val newId = ArendPsiFactory(myProject).createFromText("\\func foo \\alias $newName")?.childOfType<ArendAlias>()!!.id!!
                (element as? ReferableAdapter<*>)?.getAlias()?.id?.replaceWithNotification(newId)
            }
        }
    }
}