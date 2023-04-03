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
import org.arend.psi.ext.ArendCaseArg
import org.arend.psi.ext.ArendCaseExpr
import org.arend.psi.ext.ArendRefIdentifier
import org.arend.psi.nextElement
import org.arend.typechecking.error.local.ElimSubstError
import org.arend.util.ArendBundle

class ElimSubstQuickFix(
    private val cause: SmartPsiElementPointer<PsiElement>,
    private val error: ElimSubstError
) : IntentionAction {

    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = ArendBundle.message("arend.elim.substitute")

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = cause.element != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        when (cause.element) {
            is ArendCaseExpr -> solveCaseExpr(project)
            is ArendRefIdentifier -> solveDefIdentifier()
        }
    }

    private fun solveCaseExpr(project: Project) {
        val caseExpr = cause.element ?: return
        val argument = notEliminatedRegex.find(error.toString())!!.groupValues[1]

        val psiFactory = ArendPsiFactory(project)
        val caseArg = psiFactory.createCaseArg(getElimCaseArgument(argument))!!

        val elimElement = caseExpr.findChildByText(argument)
        if (elimElement != null) {
            val nextElement = elimElement.nextElement
            if (elimElement.nextElement != null) {
                caseExpr.addBefore(caseArg, nextElement)
            } else {
                caseExpr.add(caseArg)
            }
            elimElement.delete()
        } else {
            val lastCaseArg = caseExpr.childrenOfType<ArendCaseArg>().last()
            var comma = psiFactory.createFromText(",")?.childOfType<PsiElement>()!!.children[0].children[0]
            var whiteSpace = psiFactory.createWhitespace(" ")

            comma = if (lastCaseArg.nextElement?.text != ",") {
                caseExpr.addAfter(comma, lastCaseArg)
            } else {
                lastCaseArg.nextElement!!
            }
            whiteSpace = caseExpr.addAfter(whiteSpace, comma)
            caseExpr.addAfter(caseArg, whiteSpace)
        }
    }

    private fun solveDefIdentifier() {
        var element: PsiElement? = cause.element
        while (element !is ArendCaseArg) {
            element = element?.parent
            if (element == null) {
                return
            }
        }

        val caseExpr = element.parent
        val argument = bindingAfterRegex.find(error.toString())!!.groupValues[1]

        val elimElement = caseExpr.findChildByText(getElimCaseArgument(argument))!!
        val tempElement = element.copy()

        element.replace(elimElement)
        caseExpr.findChildByText(getElimCaseArgument(argument))!!.replace(tempElement)
    }

    private fun PsiElement.findChildByText(text: String) = children.find { it.text == text }

    private fun getElimCaseArgument(argument: String) = "\\elim $argument"

    companion object {
        private val bindingAfterRegex = "Binding (.+) should be eliminated after".toRegex()
        private val notEliminatedRegex = "Cannot perform substitution since binding (.+) is not eliminated".toRegex()
    }
}
