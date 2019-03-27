package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.arend.psi.ArendCoClause
import org.arend.psi.ext.ArendNewExprImplMixin
import org.arend.quickfix.CoClausesKey
import org.arend.quickfix.ImplementFieldsQuickFix
import org.arend.quickfix.NewExprAnnotator

class ImplementFieldsInNewExprIntention : SelfTargetingIntention<ArendNewExprImplMixin>(ArendNewExprImplMixin::class.java, "Implement fields in \\new expression") {

    override fun isApplicableTo(element: ArendNewExprImplMixin, caretOffset: Int): Boolean {
        if (element.getNewKw() == null) return false
        val data = element.getUserData(CoClausesKey.INSTANCE)
        return data != null && data.isNotEmpty()
    }

    override fun applyTo(element: ArendNewExprImplMixin, project: Project?, editor: Editor?) {
        val appExpr = element.getArgumentAppExpr()
        val data = element.getUserData(CoClausesKey.INSTANCE)
        if (data != null && appExpr != null && project != null)
            ImplementFieldsQuickFix(NewExprAnnotator(element, appExpr), data, "").invoke(project, editor, null)
    }

    override fun forbidCaretInsideElement(element: PsiElement): Boolean =
        element is ArendCoClause && (element.parent as? ArendNewExprImplMixin)?.getNewKw() != null
}