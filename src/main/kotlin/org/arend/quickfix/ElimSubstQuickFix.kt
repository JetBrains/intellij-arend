package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.childrenOfType
import org.arend.naming.reference.DataLocalReferable
import org.arend.psi.ArendPsiFactory
import org.arend.psi.childOfType
import org.arend.psi.ext.*
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
        val psiFactory = ArendPsiFactory(project)
        when (cause.element) {
            is ArendCaseExpr -> solveCaseExpr(psiFactory)
            is ArendRefIdentifier -> {
                val infoElimArguments = solveDefIdentifier(psiFactory) ?: return
                changePatternMatchingList(infoElimArguments, psiFactory)
            }
        }
    }

    private fun solveCaseExpr(psiFactory: ArendPsiFactory) {
        val caseExpr = cause.element ?: return

        error.notEliminatedBindings.map { argument ->
            (argument as? DataLocalReferable?)?.refName ?: ""
        }.filter { it != "" }.forEach {
            val caseArg = psiFactory.createCaseArg(getElimCaseArgument(it))!!

            val childElement = caseExpr.findChildByText(it)
            if (childElement != null) {
                val nextElement = childElement.nextElement
                if (nextElement != null) {
                    caseExpr.addBefore(caseArg, nextElement)
                } else {
                    caseExpr.add(caseArg)
                }
                childElement.delete()
            } else {
                val lastCaseArg = caseExpr.childrenOfType<ArendCaseArg>().last()
                var comma = psiFactory.createComma()
                var whiteSpace = psiFactory.createWhitespace(" ")

                val nextElement = lastCaseArg.nextElement
                comma = if (nextElement?.text != ",") {
                    caseExpr.addAfter(comma, lastCaseArg)
                } else {
                    nextElement
                }
                whiteSpace = caseExpr.addAfter(whiteSpace, comma)
                caseExpr.addAfter(caseArg, whiteSpace)
            }
        }
    }

    private fun solveDefIdentifier(psiFactory: ArendPsiFactory): InfoElimArguments? {
        var element = cause.element
        while (element !is ArendCaseArg) {
            element = element?.parent
            if (element == null) {
                return null
            }
        }

        val caseExpr = element.parent
        val arguments = error.notEliminatedBindings.map { argument ->
            (argument as? DataLocalReferable?)?.refName ?: ""
        }.filter { it != "" }.map {
            caseExpr.findChildByText(getElimCaseArgument(it))
        }

        val elements = caseExpr.children.filterIsInstance<ArendCaseArg>()
        val firstElimArgumentIndex = elements.indexOfFirst { arguments.contains(it) }
        val elementIndex = elements.indexOf(element)

        swapElements(elementIndex, elements, firstElimArgumentIndex, psiFactory)

        return InfoElimArguments(
            elementIndex,
            firstElimArgumentIndex,
            elements.size,
            caseExpr as ArendCaseExpr
        )
    }

    private fun changePatternMatchingList(infoElimArguments: InfoElimArguments, psiFactory: ArendPsiFactory) {
        val arendPatterns = infoElimArguments.caseExpr.childOfType<ArendWithBody>()?.childOfType<ArendClause>()
            ?.childrenOfType<ArendPattern>() ?: return
        if (infoElimArguments.elementSize != arendPatterns.size) {
            return
        }
        swapElements(
            infoElimArguments.elementIndex,
            arendPatterns,
            infoElimArguments.firstElimArgumentIndex,
            psiFactory
        )
    }

    private fun swapElements(
        elementIndex: Int,
        elements: List<PsiElement>,
        firstElimArgumentIndex: Int,
        psiFactory: ArendPsiFactory
    ) {
        val element = elements[elementIndex]
        val parent = element.parent

        if (elements.lastIndex == elementIndex) {
            deleteSymbol(elements[elementIndex - 1], ",")
        }

        var comma = psiFactory.createComma()
        val whiteSpace = psiFactory.createWhitespace(" ")

        val firstElimArgument = elements[firstElimArgumentIndex]
        val newElement = parent.addBefore(element, firstElimArgument)
        comma = parent.addAfter(comma, newElement)
        parent.addAfter(whiteSpace, comma)

        deleteSymbol(element, " ")
        element.delete()
    }

    private fun deleteSymbol(prevElement: PsiElement, symbol: String) {
        val maybeSymbol = prevElement.nextElement
        if (maybeSymbol?.text == symbol) {
            maybeSymbol.delete()
        }
    }

    private fun PsiElement.findChildByText(text: String) = children.find { it.text == text }

    private fun getElimCaseArgument(argument: String) = "\\elim $argument"

    private class InfoElimArguments(
        val elementIndex: Int,
        val firstElimArgumentIndex: Int,
        val elementSize: Int,
        val caseExpr: ArendCaseExpr
    )
}
