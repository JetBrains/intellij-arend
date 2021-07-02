package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
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
        val range = element.textRange
        val openBracket = if (editor.document.text[range.startOffset] == '(') "{" else "("
        val closeBracket = if (editor.document.text[range.endOffset - 1] == ')') "}" else ")"

        editor.document.replaceString(range.startOffset, range.startOffset + 1, openBracket)
        editor.document.replaceString(range.endOffset - 1, range.endOffset, closeBracket)
    }
}
