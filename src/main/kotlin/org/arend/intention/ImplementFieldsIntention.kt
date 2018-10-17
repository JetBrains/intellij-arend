package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.arend.psi.ext.ArendNewExprImplMixin
import org.arend.quickfix.AbstractEWCCAnnotator
import org.arend.quickfix.ImplementFieldsQuickFix
import org.arend.quickfix.NewExprAnnotator

class ImplementFieldsIntention: SelfTargetingIntention<ArendNewExprImplMixin>(ArendNewExprImplMixin::class.java, AbstractEWCCAnnotator.IMPLEMENT_MISSING_FIELDS){
    override fun isApplicableTo(element: ArendNewExprImplMixin, caretOffset: Int): Boolean {
        val appExpr = element.getArgumentAppExpr()
        return appExpr != null && !NewExprAnnotator(element, appExpr).doAnnotate(null, "").isEmpty()
    }

    override fun applyTo(element: ArendNewExprImplMixin, project: Project?, editor: Editor?) {
        val appExpr = element.getArgumentAppExpr()
        if (appExpr != null) {
            val annotator = NewExprAnnotator(element, appExpr)
            val fields = annotator.doAnnotate(null, "")
            if (!fields.isEmpty() && project != null) {
                val fixIntention = ImplementFieldsQuickFix(annotator, fields, "")
                fixIntention.invoke(project, editor, null)
            }
        }

    }

}