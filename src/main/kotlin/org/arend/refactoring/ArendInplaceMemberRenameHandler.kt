package org.arend.refactoring

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.psi.*
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer
import org.arend.naming.reference.GlobalReferable

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
            if (aliasUnderCaret != null) return ArendInplaceRenamer(elementToRename, element, editor, project, aliasUnderCaret)
        }

        return super.createMemberRenamer(element, elementToRename, editor)
    }

}

class ArendInplaceRenamer(elementToRename: PsiNamedElement,
                          substituted: PsiElement?,
                          editor: Editor,
                          val project: Project,
                          val aliasUnderCaret: Boolean): MemberInplaceRenamer(elementToRename, substituted, editor) {
    override fun getInitialName(): String {
        val element = myElementToRename
        if (element is GlobalReferable) {
            return if (aliasUnderCaret) element.aliasName!! else element.refName
        }
        return super.getInitialName()
    }

    override fun acceptReference(reference: PsiReference?): Boolean {
        if (reference != null) {
            val element = reference.element
            val textRange = getRangeToRename(reference)
            val referenceText = element.text.substring(textRange.startOffset, textRange.endOffset)
            return Comparing.strEqual(referenceText, if (aliasUnderCaret) (myElementToRename as GlobalReferable).aliasName else (myElementToRename as GlobalReferable).refName)
        }
        return super.acceptReference(reference)
    }

    override fun createRenameProcessor(element: PsiElement?, newName: String?): RenameProcessor {
        return super.createRenameProcessor(element, newName) //TODO: Implement proper rename processor
    }
}