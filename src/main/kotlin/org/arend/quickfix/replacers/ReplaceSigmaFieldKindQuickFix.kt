package org.arend.quickfix.replacers

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.util.ArendBundle

class ReplaceSigmaFieldKindQuickFix(private val kwRef: SmartPsiElementPointer<PsiElement>) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = kwRef.element != null

    override fun getText(): String = ArendBundle.message("arend.replace.sigma.kind")

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        kwRef.element?.delete()
    }
}