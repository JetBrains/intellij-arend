package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import org.arend.psi.ArendCoClause
import org.arend.psi.ext.ArendNewExprImplMixin
import org.arend.quickfix.implementCoClause.CoClausesKey
import org.arend.quickfix.implementCoClause.ImplementFieldsQuickFix

class ImplementFieldsInNewExprIntention : SelfTargetingIntention<ArendNewExprImplMixin>(ArendNewExprImplMixin::class.java, "Implement fields in \\new expression") {

    override fun isApplicableTo(element: ArendNewExprImplMixin, caretOffset: Int, editor: Editor): Boolean {
        if (element.appPrefix?.newKw == null) return false
        val data = element.getUserData(CoClausesKey)
        return data != null && data.isNotEmpty()
    }

    override fun applyTo(element: ArendNewExprImplMixin, project: Project, editor: Editor) {
        val data = element.getUserData(CoClausesKey) ?: return
        ImplementFieldsQuickFix(SmartPointerManager.createPointer(element), false, data).invoke(project, editor, null)
    }

    override fun forbidCaretInsideElement(element: PsiElement): Boolean =
        element is ArendCoClause && (element.parent as? ArendNewExprImplMixin)?.appPrefix?.newKw != null
}