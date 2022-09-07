package org.arend.liveTemplates

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import org.arend.codeInsight.completion.ArendCompletionContributor
import org.arend.codeInsight.completion.ArendCompletionContributor.Companion.jointOfStatementsCondition
import org.arend.psi.ArendFile
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

abstract class ArendTemplateContextType(
    id: @NonNls String,
    presentableName: @Nls String,
    baseContextType: Class<out TemplateContextType>?
) : TemplateContextType(id, presentableName, baseContextType) {

    override fun isInContext(templateActionContext: TemplateActionContext): Boolean {
        val file = templateActionContext.file as? ArendFile ?: return false
        val element = file.findElementAt(templateActionContext.startOffset) ?: return false
        return isInContext(element)
    }

    protected open fun isInContext(element: PsiElement): Boolean = element !is PsiComment

    class Everywhere : ArendTemplateContextType("AREND", "Arend", null)

    class Statement : ArendTemplateContextType("AREND_STATEMENT", "Statement", Everywhere::class.java) {
        override fun isInContext(templateActionContext: TemplateActionContext): Boolean {
            return super.isInContext(templateActionContext) &&
                    jointOfStatementsCondition(ArendCompletionContributor.ArendCompletionParameters(templateActionContext.startOffset, templateActionContext.file.originalFile))
        }

        override fun isInContext(element: PsiElement): Boolean =
            super.isInContext(element) && ArendCompletionContributor.STATEMENT_END_CONTEXT.accepts(element)
    }

    class Expression : ArendTemplateContextType("AREND_EXPRESSION", "Expression", Everywhere::class.java) {
        override fun isInContext(element: PsiElement): Boolean =
            super.isInContext(element) && ArendCompletionContributor.BASIC_EXPRESSION_KW_PATTERN.accepts(element)
    }
}