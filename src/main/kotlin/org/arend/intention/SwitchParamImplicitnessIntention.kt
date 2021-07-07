package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import org.arend.codeInsight.ArendParameterInfoHandler
import org.arend.ext.error.ArgumentExplicitnessError
import org.arend.naming.reference.Referable
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.util.ArendBundle
import org.arend.psi.ext.PsiReferable
import org.arend.term.abs.Abstract
import kotlin.streams.toList

class SwitchParamImplicitnessIntention : SelfTargetingIntention<ArendCompositeElement>(
    ArendCompositeElement::class.java,
    ArendBundle.message("arend.coClause.switchParamImplicitness")
) {
    override fun isApplicableTo(element: ArendCompositeElement, caretOffset: Int, editor: Editor): Boolean {
        return when (element) {
            is ArendNameTele, is ArendLamTele, is ArendFieldTele, is ArendTypeTele -> true
            else -> false
        }
    }

    // TODO: implement for other tele
    override fun applyTo(element: ArendCompositeElement, project: Project, editor: Editor) {
        val psiFunction = element.ancestor<PsiReferable>() as? PsiElement
        psiFunction ?: return

        val argumentIndex = getArgumentIndex(element)

        for (psiReference in ReferencesSearch.search(psiFunction)) {
            val psiFunctionCall = psiReference.element.ancestor<ArendArgumentAppExpr>()
            psiFunctionCall ?: continue

            val parameterIndices = getParametersIndices(psiReference.element)

            for ((i, j) in parameterIndices.withIndex()) {
                println("$i -> $j")
            }

            val newPsiElement = rewriteFunctionCalling(psiFunctionCall, parameterIndices, argumentIndex)
            psiFunctionCall.replaceWithNotification(newPsiElement)
        }

        element.replaceWithNotification(rewriteFunctionDef(element))
    }

    private fun getArgumentIndex(element: ArendCompositeElement): Int {
        var elementIndex = 0
        var i = 0
        for (args in element.parent.children) {
            if (args is ArendNameTele) {
                if (args == element) {
                    elementIndex = i
                    break
                }
                for (child in args.children) {
                    if (child is ArendIdentifierOrUnknown) i++
                    if (child.text.equals(":")) break
                }
            }
        }

        return elementIndex
    }

    private fun getParametersIndices(psiFunctionUsage: PsiElement): List<Int> {
        val psiFunctionCall = psiFunctionUsage.ancestor<ArendArgumentAppExpr>()
        psiFunctionCall ?: return emptyList()

        val parameterHandler = ArendParameterInfoHandler()
        val container = parameterHandler.extractRefFromSourceNode(psiFunctionUsage as Abstract.SourceNode)
        val ref = container?.resolve as? Referable
        val parameters = ref?.let { parameterHandler.getAllParametersForReferable(it) }
        parameters ?: return emptyList()

        val argsExplicitness = getArgumentsExplicitness(psiFunctionCall)

        val argsIndices = mutableListOf<Int>()
        for (i in psiFunctionCall.argumentList.indices) {
            argsIndices.add(parameterHandler.findParamIndex(parameters, argsExplicitness.subList(0, i + 1)))
        }

        return argsIndices
    }

    private fun getArgumentsExplicitness(psiFunctionCall: ArendArgumentAppExpr): List<Boolean> {
        return psiFunctionCall.argumentList.stream().map { it.text.first() != '{' }.toList()
    }

    private fun rewriteFunctionDef(psiDefFunction: ArendCompositeElement): PsiElement {
        val factory = ArendPsiFactory(psiDefFunction.project)
        val isExplicit = (psiDefFunction.text.first() == '(')
        val text = with(psiDefFunction.text) {
            substring(1, length - 1)
        }
        val (params, type) = text.split(":")
        return factory.createNameTele(params.trim().trimEnd(), type.trim().trimEnd(), !isExplicit)
    }

    private fun rewriteFunctionCalling(
        psiFunctionCall: ArendArgumentAppExpr,
        parameterIndices: List<Int>,
        elementIndex: Int
    ): PsiElement {
        val elementIndexInArgs = parameterIndices.indexOf(elementIndex)

        if (elementIndexInArgs == -1) {
            TODO()
        }

        fun rewriteArg(text: String, toExplicit: Boolean): String {
            var newText = ""

            if (toExplicit) {
                newText = text.substring(1, text.length - 1)
                if (text.contains(" ")) {
                    newText = "($newText)"
                }
                return newText
            }

            newText = if (text.first() == '(') text.substring(1, text.length - 1) else text
            return "{$newText}"
        }

        val expr = buildString {
            append(psiFunctionCall.atomFieldsAcc?.text + " ")

            for ((i, arg) in psiFunctionCall.argumentList.withIndex()) {
                if (parameterIndices[i] != elementIndex) {
                    append(arg.text + " ")
                } else {
                    append(rewriteArg(arg.text, arg.text.first() == '{') + " ")
                }
            }
        }

        val factory = ArendPsiFactory(psiFunctionCall.project)
        return factory.createExpression(expr)
    }
}
