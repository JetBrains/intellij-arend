package org.arend.quickfix.removers

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.arend.psi.*
import org.arend.psi.ext.ArendArgument
import org.arend.psi.ext.ArendTuple
import org.arend.psi.ext.ArendTupleExpr

class RemoveTupleExprQuickFix(private val message: String, private val tupleExpr: SmartPsiElementPointer<ArendTupleExpr>, private val removeArgument: Boolean) : IntentionAction {
    override fun startInWriteAction() = true

    override fun getText() = message

    override fun getFamilyName() = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = tupleExpr.element != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val element = tupleExpr.element ?: return
        val next = element.findNextSibling()
        if (next is LeafPsiElement && next.elementType == ArendElementTypes.COMMA) {
            element.parent?.deleteChildRange(element, next)
            return
        }

        val prev = element.findPrevSibling()
        if (prev is LeafPsiElement && prev.elementType == ArendElementTypes.COMMA) {
            element.parent?.deleteChildRange(prev, element)
            return
        }

        if (removeArgument) {
            when (val parent = element.parent) {
                is ArendArgument -> parent.deleteWithWhitespaces()
                is ArendTuple -> (parent.topmostEquivalentSourceNode.parent as? ArendArgument)?.deleteWithWhitespaces()
            }
        } else {
            element.delete()
        }
    }
}