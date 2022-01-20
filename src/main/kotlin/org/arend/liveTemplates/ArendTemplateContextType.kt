package org.arend.liveTemplates

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
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
}