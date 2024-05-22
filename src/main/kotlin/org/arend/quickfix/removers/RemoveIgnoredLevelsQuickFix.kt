package org.arend.quickfix.removers

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.psi.childOfType
import org.arend.psi.deleteWithWhitespaces
import org.arend.psi.ext.ArendLevelsExpr
import org.arend.util.ArendBundle

class RemoveIgnoredLevelsQuickFix(private val cause: SmartPsiElementPointer<PsiElement>) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = ArendBundle.message("arend.remove.levels")

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = cause.element != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val parent = cause.element?.parent
        val levels = parent?.childOfType<ArendLevelsExpr>()
        levels?.deleteWithWhitespaces()
    }
}
