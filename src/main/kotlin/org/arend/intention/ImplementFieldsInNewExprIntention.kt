package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.arend.psi.ArendCoClause
import org.arend.psi.ext.ArendNewExprImplMixin
import org.arend.quickfix.AbstractEWCCAnnotator
import org.arend.quickfix.CoClausesKey
import org.arend.quickfix.ImplementFieldsQuickFix

class ImplementFieldsInNewExprIntention : SelfTargetingIntention<ArendNewExprImplMixin>(ArendNewExprImplMixin::class.java, "Implement fields in \\new expression") {

    override fun isApplicableTo(element: ArendNewExprImplMixin, caretOffset: Int, editor: Editor?): Boolean {
        if (element.newKw == null) return false
        val data = element.getUserData(CoClausesKey)
        return data != null && data.isNotEmpty()
    }

    override fun applyTo(element: ArendNewExprImplMixin, project: Project?, editor: Editor?) {
        project ?: return
        val data = element.getUserData(CoClausesKey) ?: return
        AbstractEWCCAnnotator.makeAnnotator(element)?.let {
            ImplementFieldsQuickFix(it, data).invoke(project, editor, null)
        }
    }

    override fun forbidCaretInsideElement(element: PsiElement): Boolean =
        element is ArendCoClause && (element.parent as? ArendNewExprImplMixin)?.newKw != null
}