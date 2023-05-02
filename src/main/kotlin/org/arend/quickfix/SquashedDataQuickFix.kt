package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.psi.ArendPsiFactory
import org.arend.psi.childOfType
import org.arend.psi.ext.*
import org.arend.psi.nextElement
import org.arend.util.ArendBundle

class SquashedDataQuickFix(private val cause: SmartPsiElementPointer<PsiElement>) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = ArendBundle.message("arend.squashedData.changeKeyword")

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = cause.element != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        var element: PsiElement? = cause.element
        while (element !is ArendCaseExpr && element !is ArendDefFunction) {
            element = element?.parent
            if (element == null) {
                return
            }
        }

        val psiFactory = ArendPsiFactory(project)
        when (element) {
            is ArendCaseExpr -> {
                changeCaseExpr(psiFactory, element)
                updateReturnExpression(psiFactory, element)
            }

            is ArendDefFunction -> changeDefFunction(psiFactory, element)
        }
    }

    private fun changeCaseExpr(psiFactory: ArendPsiFactory, element: ArendCaseExpr) {
        val scaseElement = psiFactory.createExpression("\\scase t").firstChild
        val caseElement = element.firstChild

        caseElement.replace(scaseElement)
    }

    private fun changeDefFunction(psiFactory: ArendPsiFactory, element: ArendDefFunction) {
        val sfuncElement = psiFactory.createFunctionKeyword("\\sfunc")
        val funcElement = element.firstChild

        funcElement.replace(sfuncElement)
    }
}
