package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.childrenOfType
import org.arend.psi.ArendPsiFactory
import org.arend.psi.childOfType
import org.arend.psi.ext.ArendIdentifierOrUnknown
import org.arend.psi.ext.ArendPiExpr
import org.arend.psi.ext.ArendReturnExpr
import org.arend.typechecking.error.local.ImplicitLambdaError
import org.arend.util.ArendBundle

class ImplicitLambdaQuickFix(private val cause: SmartPsiElementPointer<PsiElement>, private val error: ImplicitLambdaError): IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = ArendBundle.message("arend.lambda.argument.implicitness")

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = cause.element != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val element = cause.element!!
        val parameter = lambdaImplicitParameterRegex.find(error.toString())!!.groupValues[1]

        var index = element.children.indexOfFirst { it.text == parameter }
        for (child in element.parent.children) {
            if (child == element) {
                break
            }
            index += child.childrenOfType<ArendIdentifierOrUnknown>().size
        }

        val returnExpr = element.parent.parent.parent.childOfType<ArendReturnExpr>()?.childOfType<ArendPiExpr>() ?: return

        returnExpr.children.getOrNull(index)?.let {
            val psiFactory = ArendPsiFactory(project)
            val nameTele = psiFactory.createTypeTele(parameter, it.text, false)
            it.replace(nameTele)
        }
    }

    companion object {
        private val lambdaImplicitParameterRegex = "Parameter '(.+)' is implicit, but the corresponding parameter of the expected type is not".toRegex()
    }
}
