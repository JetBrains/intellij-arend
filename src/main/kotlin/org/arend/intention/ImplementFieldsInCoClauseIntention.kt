package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.arend.psi.ArendCoClause
import org.arend.psi.ArendExpr
import org.arend.psi.ext.ArendCoClauseImplMixin
import org.arend.quickfix.CoClauseAnnotator
import org.arend.quickfix.CoClausesKey
import org.arend.quickfix.ImplementFieldsQuickFix
import org.arend.quickfix.InstanceQuickFixAnnotation

class ImplementFieldsInCoClauseIntention : SelfTargetingIntention<ArendCoClauseImplMixin>(ArendCoClauseImplMixin::class.java, "Implement fields of a super class") {

    override fun isApplicableTo(element: ArendCoClauseImplMixin, caretOffset: Int): Boolean {
        val data = element.getUserData(CoClausesKey.INSTANCE)
        if (data != null && data.isNotEmpty()) {
            text = "Implement fields of ${element.getLongName()?.text}"
            return true
        }
        return false
    }

    override fun applyTo(element: ArendCoClauseImplMixin, project: Project?, editor: Editor?) {
        val data = element.getUserData(CoClausesKey.INSTANCE)
        val rangeToReport = element.getLongName()?.textRange
        if (data != null && rangeToReport != null && project != null)
            ImplementFieldsQuickFix(CoClauseAnnotator(element, rangeToReport, InstanceQuickFixAnnotation.NO_ANNOTATION), data, "").invoke(project, editor, null)
    }

    override fun forbidCaretInsideElement(element: PsiElement): Boolean = element is ArendExpr || element is ArendCoClause
}