package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.util.ArendBundle

class SwitchParamImplicitnessIntention : SelfTargetingIntention<ArendCompositeElement> (ArendCompositeElement::class.java, ArendBundle.message("arend.coClause.switchParamImplicitness")) {
    override fun isApplicableTo(element: ArendCompositeElement, caretOffset: Int, editor: Editor): Boolean {
        return when (element) {
            is ArendNameTele, is ArendLamTele, is ArendFieldTele, is ArendTypeTele -> true
            else -> false
        }
    }

    override fun applyTo(element: ArendCompositeElement, project: Project, editor: Editor) {
        val isExplicit = (element.text.first() == '(')
        val factory = ArendPsiFactory(element.project)
        // TODO: implement for other tele
        val text = with(element.text) {
            substring(1, length - 1)
        }
        val (params, type) = text.split(":")
        val newElement = factory.createNameTele(params.trim().trimEnd(), type.trim().trimEnd(), !isExplicit)
        element.replaceWithNotification(newElement)
    }
}
