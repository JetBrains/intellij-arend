package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.util.ArendBundle
import org.arend.psi.ext.PsiReferable

class SwitchParamImplicitnessIntention : SelfTargetingIntention<ArendCompositeElement> (ArendCompositeElement::class.java, ArendBundle.message("arend.coClause.switchParamImplicitness")) {
    override fun isApplicableTo(element: ArendCompositeElement, caretOffset: Int, editor: Editor): Boolean {
        return when (element) {
            is ArendNameTele, is ArendLamTele, is ArendFieldTele, is ArendTypeTele -> true
            else -> false
        }
    }

    // TODO: implement for other tele
    override fun applyTo(element: ArendCompositeElement, project: Project, editor: Editor) {
        val isExplicit = (element.text.first() == '(')
        val factory = ArendPsiFactory(element.project)
        val text = with(element.text) {
            substring(1, length - 1)
        }

        val (elementIndex, isExplicitArg) = markArguments(element)
        println(elementIndex)
        val (params, type) = text.split(":")
        val newElement = factory.createNameTele(params.trim().trimEnd(), type.trim().trimEnd(), !isExplicit)
        val replaced = element.replaceWithNotification(newElement)

        val psiFunction = replaced.ancestor<PsiReferable>() as? PsiElement
        psiFunction ?: return

        for (psiReference in ReferencesSearch.search(psiFunction)) {
            val psiUsageElement = psiReference.element.ancestor<ArendArgumentAppExpr>() as? PsiElement
            psiUsageElement ?: continue
            val newPsi = rewriteFunctionCalling(psiUsageElement, isExplicitArg, elementIndex)
            psiUsageElement.replaceWithNotification(newPsi)
        }
    }

    private fun markArguments(element: ArendCompositeElement): Pair<Int, List<Boolean>> {
        // isExplicitArg[i] = true <=> ith parameter is implicit
        val isExplicitArg = mutableListOf<Boolean>()
        var elementIndex = 0
        var i = 0
        for (args in element.parent.children) {
            if (args is ArendNameTele) {
                if (args == element) {
                    elementIndex = i
                }
                for (child in args.children) {
                    if (child is ArendIdentifierOrUnknown) {
                        isExplicitArg.add((args.text.first() == '('))
                        i++
                    }

                    if (child.text == ":") break
                }
            }
        }

        return Pair(elementIndex, isExplicitArg)
    }

    private fun rewriteFunctionCalling(
        psiFunctionCall: PsiElement,
        isExplicitArg: List<Boolean>,
        elementIndex: Int
    ): PsiElement {
        val expr = buildString {
            var i = 0
            for (child in psiFunctionCall.children) {
                if (child is ArendArgument) {
                    if (i == elementIndex) {
                        var newText = child.text
                        if (isExplicitArg[elementIndex]) {
                            newText = "{$newText}"
                        } else {
                            newText = newText.substring(1, newText.length - 1)
                        }
                        append("$newText ")
                    } else {
                        append(child.text + " ")
                    }
                    i++
                } else {
                    append(child.text + " ")
                }
            }
        }

        val factory = ArendPsiFactory(psiFunctionCall.project)
        return factory.createExpression(expr)
    }
}
