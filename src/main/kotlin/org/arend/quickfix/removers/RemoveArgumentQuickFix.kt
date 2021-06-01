package org.arend.quickfix.removers

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.psi.ArendArgument
import org.arend.psi.deleteWithNotification
import org.arend.util.ArendBundle

class RemoveArgumentQuickFix(private val argument: SmartPsiElementPointer<ArendArgument>) : IntentionAction {
    override fun startInWriteAction() = true

    override fun getText() = ArendBundle.message("arend.expression.removeArgument")

    override fun getFamilyName() = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = argument.element != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        argument.element?.deleteWithNotification()
    }
}