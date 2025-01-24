package org.arend.liveTemplates

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import org.arend.codeInsight.completion.ArendCompletionContributor
import org.arend.codeInsight.completion.ArendCompletionContributor.Companion.jointOfStatementsCondition
import org.arend.psi.ArendFile
import org.jetbrains.annotations.Nls

abstract class ArendTemplateContextType(@Nls presentableName: String) : TemplateContextType(presentableName) {

    override fun isInContext(templateActionContext: TemplateActionContext): Boolean {
        val file = templateActionContext.file as? ArendFile ?: return false
        val element = file.findElementAt(templateActionContext.startOffset) ?: return false
        return isInContext(element)
    }

    protected open fun isInContext(element: PsiElement): Boolean = element !is PsiComment

    class Everywhere : ArendTemplateContextType("Arend")

    class Statement : ArendTemplateContextType("Statement") {
        override fun isInContext(templateActionContext: TemplateActionContext): Boolean {
            return super.isInContext(templateActionContext) &&
                    jointOfStatementsCondition(ArendCompletionContributor.ArendCompletionParameters(
                        templateActionContext.file.originalFile.findElementAt(templateActionContext.startOffset),
                        templateActionContext.startOffset, templateActionContext.file.originalFile))
        }

        override fun isInContext(element: PsiElement): Boolean =
            super.isInContext(element) && ArendCompletionContributor.STATEMENT_END_CONTEXT.accepts(element)
    }

    class Expression : ArendTemplateContextType("Expression") {
        override fun isInContext(element: PsiElement): Boolean =
            super.isInContext(element) && ArendCompletionContributor.BASIC_EXPRESSION_KW_PATTERN.accepts(element)
    }
}