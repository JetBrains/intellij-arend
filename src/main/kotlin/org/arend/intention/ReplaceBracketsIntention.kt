package org.arend.intention

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.psi.util.startOffset
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.childOfType
import org.arend.psi.ext.ArendCompositeElement
import org.arend.util.ArendBundle

class ReplaceBracketsIntention : SelfTargetingIntention<PsiElement>(PsiElement::class.java, ArendBundle.message("arend.replaceBrackets")) {

    private fun getBrackets(element: PsiElement): Pair<PsiElement?, PsiElement?> {
        var currentElement: PsiElement? = element
        while (currentElement !is ArendCompositeElement?) {
            currentElement = currentElement?.parent
        }
        if (currentElement == null) {
            return Pair(null, null)
        }

        val (left, right) = if (currentElement.childOfType(LPAREN) != null) {
            Pair(currentElement.childOfType(LPAREN), currentElement.childOfType(RPAREN))
        } else {
            Pair(currentElement.childOfType(LBRACE), currentElement.childOfType(RBRACE))
        }
        return Pair(left, right)
    }

    override fun isApplicableTo(element: PsiElement, caretOffset: Int, editor: Editor): Boolean {
        val (left, right) = getBrackets(element)
        text = if (left.elementType == LPAREN) {
            ArendBundle.message("arend.replaceBrackets")
        } else {
            ArendBundle.message("arend.replaceParentheses")
        }
        return left == element || right == element
    }

    private fun changeBrackets(element: PsiElement, document: Document) {
        ApplicationManager.getApplication().runWriteAction {
            document.replaceString(
                element.startOffset,
                element.startOffset + 1,
                when (element.text) {
                    "(" -> "{"
                    "{" -> "("
                    ")" -> "}"
                    else -> ")"
                }
            )
        }
    }

    override fun applyTo(element: PsiElement, project: Project, editor: Editor) {
        val (left, right) = getBrackets(element)

        val document = editor.document
        PsiDocumentManager.getInstance(project).commitDocument(document)

        if (left != null && right != null) {
            changeBrackets(left, document)
            changeBrackets(right, document)
        }
    }

    override fun startInWriteAction(): Boolean = false
}
