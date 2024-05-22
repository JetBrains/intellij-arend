package org.arend.quickfix.removers

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.psi.ArendElementTypes.LBRACE
import org.arend.psi.childOfType
import org.arend.psi.ext.ArendDefFunction
import org.arend.psi.ext.ArendWhere
import org.arend.util.ArendBundle

class RemoveUseLevelQuickFix(private val cause: SmartPsiElementPointer<ArendDefFunction>): IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = ArendBundle.message("arend.remove.useLevel")

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = cause.element != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        var element = cause.element as PsiElement?
        while (element !is ArendWhere) {
            element = element?.parent
            if (element == null) {
                return
            }
        }

        if (element.childOfType(LBRACE) == null) {
            element.delete()
        } else {
            cause.element?.delete()
        }
    }
}
