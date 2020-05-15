package org.arend.refactoring

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Comparing
import com.intellij.psi.*
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer
import com.intellij.refactoring.util.TextOccurrencesUtil
import com.intellij.usageView.UsageInfo
import org.arend.naming.reference.GlobalReferable
import org.arend.psi.ArendAlias
import org.arend.psi.ext.impl.ReferableAdapter

class ArendInplaceMemberRenameHandler : MemberInplaceRenameHandler() {
    override fun createMemberRenamer(element: PsiElement, elementToRename: PsiNameIdentifierOwner, editor: Editor): MemberInplaceRenamer {
        val project = editor.project
        if (project != null && elementToRename is GlobalReferable && elementToRename.hasAlias()) {
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
            val caretElement = psiFile?.findElementAt(editor.caretModel.offset)
            val aliasUnderCaret = when {
                caretElement != null && caretElement.text == elementToRename.aliasName -> true
                caretElement != null && caretElement.text == elementToRename.refName -> false
                else -> null
            }
            if (aliasUnderCaret != null) {
                val aliasName = elementToRename.aliasName
                val name = if (aliasUnderCaret && aliasName != null) aliasName else elementToRename.refName
                return ArendInplaceRenamer(elementToRename, element, editor, name, aliasUnderCaret)
            }
        }

        return super.createMemberRenamer(element, elementToRename, editor)
    }

}

class ArendInplaceRenamer(elementToRename: PsiNamedElement,
                          substituted: PsiElement?,
                          editor: Editor,
                          name: String,
                          val aliasUnderCaret: Boolean):
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

    inner class MyReference(element: ArendAlias): PsiReferenceBase<ArendAlias>(element, element.id!!.textRangeInParent){
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

    override fun createRenameProcessor(element: PsiElement, newName: String): RenameProcessor = ArendRenameProcessor(element, newName)

    private inner class ArendRenameProcessor(element: PsiElement, newName: String) :
            RenameProcessor(this@ArendInplaceRenamer.myProject, element, newName,
                    RenamePsiElementProcessor.forElement(element).isToSearchInComments(element),
                    RenamePsiElementProcessor.forElement(element).isToSearchForTextOccurrences(element)
                            && TextOccurrencesUtil.isSearchTextOccurrencesEnabled(element)) {
        override fun findUsages(): Array<UsageInfo> {
            val superUsages = super.findUsages()
            return superUsages
        }
    }
}