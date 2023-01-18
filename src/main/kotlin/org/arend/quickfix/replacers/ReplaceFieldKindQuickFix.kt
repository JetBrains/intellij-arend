package org.arend.quickfix.replacers

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.psi.ArendPsiFactory
import org.arend.util.ArendBundle

class ReplaceFieldKindQuickFix(private val kwRef: SmartPsiElementPointer<PsiElement>) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = kwRef.element != null

    override fun getText(): String = ArendBundle.message("arend.replace.field.kind")

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val kw = kwRef.element ?: return
        val factory = ArendPsiFactory(project)
        kw.replace(factory.createPipe())
    }
}