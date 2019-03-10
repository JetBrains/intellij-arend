package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.arend.psi.ext.ArendNewExprImplMixin
import org.arend.quickfix.*

class ImplementFieldsInNewExprIntention:
        SelfTargetingIntention<ArendNewExprImplMixin>(ArendNewExprImplMixin::class.java,
                "Add empty implementations in \\new expression"){

    override fun isApplicableTo(element: ArendNewExprImplMixin, caretOffset: Int): Boolean {
        if (element.getNewKw() == null) return false
        val data = element.getUserData(CoClausesKey.INSTANCE)
        setText("Add empty implementations to all fields in \\new ${element.getAppExpr()?.text} expression")
        return data != null && data.isNotEmpty()
    }

    override fun applyTo(element: ArendNewExprImplMixin, project: Project?, editor: Editor?) {
        val appExpr = element.getArgumentAppExpr()
        val data = element.getUserData(CoClausesKey.INSTANCE)
        if (data != null && appExpr != null && project != null)
            ImplementFieldsQuickFix(NewExprAnnotator(element, appExpr), data, "").invoke(project, editor, null)
    }

}