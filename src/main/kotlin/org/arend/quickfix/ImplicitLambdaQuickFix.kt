package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.childrenOfType
import org.arend.psi.ArendPsiFactory
import org.arend.psi.descendantOfType
import org.arend.psi.ext.*
import org.arend.typechecking.error.local.ImplicitLambdaError
import org.arend.util.ArendBundle

class ImplicitLambdaQuickFix(
    private val cause: SmartPsiElementPointer<PsiElement>,
    private val error: ImplicitLambdaError
) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = ArendBundle.message("arend.lambda.argument.implicitness")

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = cause.element != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val element = cause.element ?: return
        val parameter = error.parameter.textRepresentation()

        val parameterIdentifiers = element.childrenOfType<ArendIdentifierOrUnknown>()
        val typeExpr = element.descendantOfType<ArendNewExpr>()?.text

        val psiFactory = ArendPsiFactory(project)
        val nameTele = psiFactory.createLamTele(parameter, typeExpr, true)

        element.parent.addAfter(nameTele, element)

        if (parameterIdentifiers.size == 1) {
            element.delete()
        } else {
            parameterIdentifiers.find { it.text == parameter }?.delete()
        }
    }
}
