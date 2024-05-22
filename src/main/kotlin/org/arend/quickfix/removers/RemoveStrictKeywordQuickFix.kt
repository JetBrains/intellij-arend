package org.arend.quickfix.removers

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.psi.ArendElementTypes.STRICT_KW
import org.arend.psi.childOfType
import org.arend.psi.deleteWithWhitespaces
import org.arend.util.ArendBundle

class RemoveStrictKeywordQuickFix(private val cause: SmartPsiElementPointer<PsiElement>) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = ArendBundle.message("arend.remove.strict")

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = cause.element?.childOfType(STRICT_KW) != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        cause.element?.childOfType(STRICT_KW)?.deleteWithWhitespaces()
    }
}
