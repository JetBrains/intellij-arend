package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import org.arend.codeInsight.ArendParameterInfoHandler
import org.arend.naming.reference.Referable
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
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

    override fun applyTo(element: ArendCompositeElement, project: Project, editor: Editor) {
        chooseApplier(element)?.applyTo(element)
    }

    // TODO: implement for other tele
    private fun chooseApplier(element: ArendCompositeElement): SwitchParamImplicitnessApplier? {
        return when (element) {
            is ArendNameTele, is ArendFieldTele -> SwitchParamImplicitnessNameFieldApplier()
            is ArendTypeTele -> SwitchParamImplicitnessTypeApplier()
            is ArendLamTele -> TODO("implement  applier for lamTele")
            else -> null
        }
    }
}

abstract class SwitchParamImplicitnessApplier {
    fun applyTo(element: ArendCompositeElement) {
        val psiFunction = element.ancestor<PsiReferable>() as? PsiElement
        psiFunction ?: return

        val argumentIndex = getArgumentIndex(element)

        for (psiReference in ReferencesSearch.search(psiFunction)) {
            val psiFunctionUsage = psiReference.element
            val psiFunctionCall = getPsiFunctionCall(psiReference.element)
            psiFunctionCall ?: continue

            val newPsiElement = rewriteFunctionCalling(
                psiFunctionUsage,
                psiFunctionCall,
                argumentIndex,
                psiFunctionCall.project
            )
            psiFunctionCall.replaceWithNotification(newPsiElement)
        }

        element.replaceWithNotification(rewriteFunctionDef(element))
    }

    private fun getParametersIndices(psiFunctionUsage: PsiElement): List<Int> {
        val psiFunctionCall = getPsiFunctionCall(psiFunctionUsage)
        // psiFunctionUsage.ancestor<ArendArgumentAppExpr>()
        psiFunctionCall ?: return emptyList()

        val parameterHandler = ArendParameterInfoHandler()
        val container = parameterHandler.extractRefFromSourceNode(psiFunctionUsage as Abstract.SourceNode)
        val ref = container?.resolve as? Referable
        val parameters = ref?.let { parameterHandler.getAllParametersForReferable(it) }
        parameters ?: return emptyList()

//        val argsExplicitness = psiFunctionCall.argumentList.map { it.text.first() != '{' }
        val argsExplicitness = getCallingParameters(psiFunctionCall).map { it.first() != '{' }
        val argsIndices = mutableListOf<Int>()
        for (i in argsExplicitness.indices) {
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
        psiFunctionUsage: PsiElement,
        psiFunctionCall: PsiElement,
        argumentIndex: Int,
        project: Project
    ): PsiElement {
        val functionName = psiFunctionUsage.text
        val parameters = getCallingParameters(psiFunctionCall)
        val parameterIndices = getParametersIndices(psiFunctionUsage)

        val elementIndexInArgs = parameterIndices.indexOf(argumentIndex)
        val argsText = parameters as MutableList<String>
        val indices: MutableList<Int> = parameterIndices as MutableList<Int>

        if (elementIndexInArgs == -1) {
            val insertAfterIndex = -parameterIndices.binarySearch(argumentIndex) - 1
            argsText.add(insertAfterIndex, "{_}")
            indices.add(insertAfterIndex, argumentIndex)
        }

        val expr = buildFunctionCallingText(functionName, argsText, indices, argumentIndex)
        return createPsiFromText(expr.trimEnd(), psiFunctionCall, project)
    }

    abstract fun getPsiFunctionCall(element: PsiElement): PsiElement?

    abstract fun getCallingParameters(element: PsiElement): List<String>

    private fun buildFunctionCallingText(
        functionName: String,
        argsText: List<String>,
        indices: List<Int>,
        switchedArgIndex: Int
    ): String {
        return buildString {
            append("$functionName ")
            for ((i, arg) in argsText.withIndex()) {
                if (indices[i] != switchedArgIndex) {
                    append("$arg ")
                } else {
                    val isNextArgExplicit = if (i != indices.size - 1) (argsText[i + 1].first() != '{') else true
                    append(rewriteArg(arg, arg.first() == '{', isNextArgExplicit) + " ")
                }
            }
        }.replace("\\s+".toRegex(), " ")
    }

    abstract fun createPsiFromText(expr: String, psiFunctionCall: PsiElement, project: Project): PsiElement

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

class SwitchParamImplicitnessNameFieldApplier : SwitchParamImplicitnessApplier() {
    override fun getPsiFunctionCall(element: PsiElement): PsiElement? {
        return element.ancestor<ArendArgumentAppExpr>()
    }

    override fun getCallingParameters(element: PsiElement): List<String> {
        val psiFunctionCall = element as ArendArgumentAppExpr
        return psiFunctionCall.argumentList.map { it.text }
    }

    override fun createPsiFromText(expr: String, psiFunctionCall: PsiElement, project: Project): PsiElement {
        val factory = ArendPsiFactory(project)
        return factory.createExpression(expr)
    }
}

class SwitchParamImplicitnessTypeApplier : SwitchParamImplicitnessApplier() {
    override fun getPsiFunctionCall(element: PsiElement): PsiElement? {
        return element.ancestor<ArendLocalCoClause>()
    }

    override fun getCallingParameters(element: PsiElement): List<String> {
        val psiFunctionCall = element as ArendLocalCoClauseImpl
        return psiFunctionCall.lamParamList.map { it.text }
    }

    override fun createPsiFromText(expr: String, psiFunctionCall: PsiElement, project: Project): PsiElement {
        val factory = ArendPsiFactory(project)
        val body = (psiFunctionCall as ArendLocalCoClause).expr?.text
        return factory.createLocalCoClause(expr, body)
    }
}
