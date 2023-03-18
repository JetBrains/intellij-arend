package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.psi.ArendPsiFactory
import org.arend.psi.ext.ArendNameTele
import org.arend.util.ArendBundle

class ImplicitnessQuickFix(private val cause: SmartPsiElementPointer<PsiElement>) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = ArendBundle.message("arend.argument.implicitness")

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = cause.element != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (cause.element?.text == null) {
            return
        }

        var element: PsiElement? = cause.element
        while (element !is ArendNameTele) {
            element = element?.parent
            if (element == null) {
                return
            }
        }

        val psiFactory = ArendPsiFactory(project)
        val nameTele = psiFactory.createNameTele(cause.element!!.text, null, false)

        runWriteAction {
            element.replace(nameTele)
        }
    }

    override fun getElementToMakeWritable(currentFile: PsiFile) = currentFile
}
