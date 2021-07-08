package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import org.arend.codeInsight.ArendParameterInfoHandler
import org.arend.naming.reference.Referable
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendLocalCoClauseImplMixin
import org.arend.util.ArendBundle
import org.arend.psi.ext.PsiReferable
import org.arend.psi.impl.ArendLocalCoClauseImpl
import org.arend.term.abs.Abstract

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
            val newPsiElement = rewriteFunctionCalling(psiFunctionCall, parameterIndices, argumentIndex)
            psiFunctionCall.replaceWithNotification(newPsiElement)
        }

        element.replaceWithNotification(rewriteFunctionDef(element))
    }

    private fun getArgumentIndex(element: ArendCompositeElement): Int {
        var elementIndex = 0
        var i = 0
        for (arg in element.parent.children) {
            when (arg) {
                is ArendNameTele, is ArendFieldTele, is ArendTypeTele -> {
                    if (arg == element) {
                        elementIndex = i
                        break
                    }

                    for (tok in arg.text.split("\\s+".toRegex())) {
                        if (tok == ":") break
                        i++
                    }
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

        val argsExplicitness = psiFunctionCall.argumentList.map { it.text.first() != '{' }
        val argsIndices = mutableListOf<Int>()
        for (i in psiFunctionCall.argumentList.indices) {
            argsIndices.add(parameterHandler.findParamIndex(parameters, argsExplicitness.subList(0, i + 1)))
        }

        return argsIndices
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
        val argsText = psiFunctionCall.argumentList.map { it.text } as MutableList<String>
        val indices: MutableList<Int> = parameterIndices as MutableList<Int>

        if (elementIndexInArgs == -1) {
            val insertAfterIndex = -parameterIndices.binarySearch(elementIndex) - 1
            argsText.add(insertAfterIndex, "{_}")
            indices.add(insertAfterIndex, elementIndex)
        }

        val expr = buildString {
            append(psiFunctionCall.atomFieldsAcc?.text + " ")

            for ((i, arg) in argsText.withIndex()) {
                if (indices[i] != elementIndex) {
                    append("$arg ")
                } else {
                    val isNextArgExplicit = if (i != indices.size - 1) (argsText[i + 1].first() != '{') else true
                    append(rewriteArg(arg, arg.first() == '{', isNextArgExplicit) + " ")
                }
            }
        }.replace("\\s+".toRegex(), " ")

        val factory = ArendPsiFactory(psiFunctionCall.project)
        return factory.createExpression(expr)
    }

    private fun rewriteArg(text: String, toExplicit: Boolean, isNextArgExplicit: Boolean): String {
        var newText: String

        if (toExplicit) {
            newText = text.substring(1, text.length - 1)
            if (text.contains(" ")) {
                newText = "($newText)"
            }
            return newText
        }

        if (text == "_" && isNextArgExplicit) {
            return ""
        }

        return "{$text}"
    }
}
