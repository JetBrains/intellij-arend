package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.psi.ArendPsiFactory
import org.arend.psi.childOfType
import org.arend.psi.ext.ArendIdentifierOrUnknown
import org.arend.psi.ext.ArendNameTele
import org.arend.typechecking.error.local.inference.LambdaInferenceError
import org.arend.util.ArendBundle

class LambdaInferenceQuickFix(
    private val cause: SmartPsiElementPointer<PsiElement>,
    private val error: LambdaInferenceError
) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = ArendBundle.message("arend.argument.inference.parameter")

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = cause.element != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val element = cause.element ?: return

        val argument = inferParameterRegex.find(error.toString())?.groupValues?.getOrNull(1) ?: return
        val argumentElement = element.findChildByText(argument) as ArendNameTele

        val psiFactory = ArendPsiFactory(project)
        val nameTele = psiFactory.createNameTele(argument, "{?}", argumentElement.isExplicit)

        argumentElement.replace(nameTele)
    }

    private fun PsiElement.findChildByText(text: String) =
        children.find {
            val element = it as ArendNameTele
            if (element.isExplicit) {
                it.text == text
            } else {
                element.childOfType<ArendIdentifierOrUnknown>()?.text == text
            }
        }

    companion object {
        private val inferParameterRegex = "Cannot infer type of parameter \'(.+)\'".toRegex()
    }
}
