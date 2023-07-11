package org.arend.quickfix.removers

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.psi.ext.ArendReturnExpr
import org.arend.refactoring.changeSignature.performTextModification
import org.arend.util.ArendBundle

class RemoveLevelQuickFix(
    private val cause: SmartPsiElementPointer<PsiElement>
) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = ArendBundle.message("arend.expression.removeLevel")

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = cause.element != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        var element = cause.element
        while (element !is ArendReturnExpr) {
            element = element?.parent
            if (element == null) {
                return
            }
        }
        val type = element.type ?: return
        performTextModification(element, type.text)
    }
}
