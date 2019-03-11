package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.arend.psi.ext.ArendCoClauseImplMixin
import org.arend.quickfix.*

class ImplementFieldsInCoClauseIntention :
        SelfTargetingIntention<ArendCoClauseImplMixin>(ArendCoClauseImplMixin::class.java,
                "Add empty implementations in a coclause corresponding to an ancestor class") {

    override fun isApplicableTo(element: ArendCoClauseImplMixin, caretOffset: Int): Boolean {
        val data = element.getUserData(CoClausesKey.INSTANCE)
        text = "Add empty implementations to all fields in coclause ${element.getLongName()?.text}"
        return data != null && data.isNotEmpty()
    }

    override fun applyTo(element: ArendCoClauseImplMixin, project: Project?, editor: Editor?) {
        val data = element.getUserData(CoClausesKey.INSTANCE)
        val rangeToReport = element.getLongName()?.textRange
        if (data != null && rangeToReport != null && project != null)
            ImplementFieldsQuickFix(CoClauseAnnotator(element, rangeToReport, InstanceQuickFixAnnotation.NO_ANNOTATION), data, "").invoke(project, editor, null)
    }
}