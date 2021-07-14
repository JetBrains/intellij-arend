package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.searches.ReferencesSearch
import org.arend.codeInsight.ArendParameterInfoHandler
import org.arend.naming.reference.Referable
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.util.ArendBundle
import org.arend.psi.ext.PsiReferable
import org.arend.refactoring.correspondedSubExpr
import org.arend.refactoring.getTele
import org.arend.refactoring.splitTele
import org.arend.term.abs.Abstract

class SwitchParamImplicitnessIntention : SelfTargetingIntention<ArendCompositeElement>(
    ArendCompositeElement::class.java,
    ArendBundle.message("arend.coClause.switchParamImplicitness")
) {
    override fun isApplicableTo(element: ArendCompositeElement, caretOffset: Int, editor: Editor): Boolean {
        return when (element) {
            is ArendNameTele, is ArendFieldTele, is ArendTypeTele -> true
            else -> false
        }
    }

    override fun applyTo(element: ArendCompositeElement, project: Project, editor: Editor) {
        val elementOnCaret = getPsiElementOnCaret(project, editor)
        val switchedArgIndexInTele = getSwitchedArgIndex(element, elementOnCaret)

        if (switchedArgIndexInTele == -1) {
            // TODO: show context window with this message
            println("[DEBUG] caret must be on argument's name")
            return
        }

        chooseApplier(element)?.applyTo(element, switchedArgIndexInTele)
    }

    private fun chooseApplier(element: ArendCompositeElement): SwitchParamImplicitnessApplier? {
        return when (element) {
            is ArendNameTele, is ArendFieldTele -> SwitchParamImplicitnessNameFieldApplier()
            is ArendTypeTele -> SwitchParamImplicitnessTypeApplier()
            else -> null
        }
    }

    private fun getSwitchedArgIndex(tele: ArendCompositeElement, switchedArg: PsiElement): Int {
        val argsText = getTeleParams(tele).map { it.text }
        return if (argsText.size == 1) 0 else argsText.indexOf(switchedArg.text)
    }

    // TODO: rewrite or remove this function
    private fun getPsiElementOnCaret(project: Project, editor: Editor): PsiElement {
        val offset = editor.caretModel.offset
        val file = FileDocumentManager.getInstance().getFile(editor.document)
        return PsiManager.getInstance(project).findFile(file!!)?.findElementAt(offset)!!
    }
}

abstract class SwitchParamImplicitnessApplier {
    fun applyTo(element: ArendCompositeElement, switchedArgIndexInTele: Int) {
        val psiFunctionDef = element.ancestor<PsiReferable>() as? PsiElement
        psiFunctionDef ?: return

        val switchedArgIndexInDef = getArgumentIndex(element) + switchedArgIndexInTele

        for (psiReference in ReferencesSearch.search(psiFunctionDef)) {
            val psiFunctionUsage = psiReference.element
            val psiFunctionCall = getTopParentPsiFunctionCall(psiFunctionUsage)

            val concreteFunctionCall =
                correspondedSubExpr(
                    psiFunctionUsage.textRange,
                    psiFunctionUsage.containingFile,
                    psiFunctionUsage.project
                )
            val functionCallText = concreteFunctionCall.subConcrete.toString()
            val prefixFunctionCall = createPsiFromText(functionCallText, psiFunctionCall)

            prefixFunctionCall ?: continue
            val newPsiElement = rewriteFunctionCalling(
                psiFunctionUsage,
                prefixFunctionCall,
                switchedArgIndexInDef
            )

            newPsiElement ?: continue
            psiFunctionCall.replaceWithNotification(newPsiElement)
        }

        rewriteFunctionDef(element, switchedArgIndexInTele)
    }

    private fun getParametersIndices(psiFunctionUsage: PsiElement, psiFunctionCall: PsiElement): List<Int> {
        val parameterHandler = ArendParameterInfoHandler()
        val container = parameterHandler.extractRefFromSourceNode(psiFunctionUsage as Abstract.SourceNode)
        val ref = container?.resolve as? Referable
        val parameters = ref?.let { parameterHandler.getAllParametersForReferable(it) }
        parameters ?: return emptyList()

        val argsExplicitness = getCallingParameters(psiFunctionCall).map { it.first() != '{' }
        val argsIndices = mutableListOf<Int>()
        for (i in argsExplicitness.indices) {
            argsIndices.add(parameterHandler.findParamIndex(parameters, argsExplicitness.subList(0, i + 1)))
        }

        return argsIndices
    }

    private fun rewriteFunctionDef(tele: ArendCompositeElement, switchedArgIndexInTele: Int) {
        val teleSize = getTele(tele)?.size
        if (teleSize != null && teleSize > 1) {
            splitTele(tele, switchedArgIndexInTele)
        }

        val factory = ArendPsiFactory(tele.project)
        val isExplicit = (tele.text.first() == '(')
        val text = with(tele.text) {
            substring(1, length - 1)
        }
        val (params, type) = text.split(":")
        val newTele = factory.createNameTele(params.trim().trimEnd(), type.trim().trimEnd(), !isExplicit)

        tele.replaceWithNotification(newTele)
    }

    private fun rewriteFunctionCalling(
        psiFunctionUsage: PsiElement,
        psiFunctionCall: PsiElement,
        argumentIndex: Int,
    ): PsiElement? {
        val functionName = psiFunctionUsage.text
        val parameters = getCallingParameters(psiFunctionCall)
        val parameterIndices = getParametersIndices(psiFunctionUsage, psiFunctionCall)

        val elementIndexInArgs = parameterIndices.indexOf(argumentIndex)
        val argsText = parameters as MutableList<String>
        val indices: MutableList<Int> = parameterIndices as MutableList<Int>

        if (elementIndexInArgs == -1) {
            val insertAfterIndex = -parameterIndices.binarySearch(argumentIndex) - 1
            argsText.add(insertAfterIndex, "{_}")
            indices.add(insertAfterIndex, argumentIndex)
        }

        val expr = buildFunctionCallingText(functionName, argsText, indices, argumentIndex)
        return createPsiFromText(expr.trimEnd(), psiFunctionCall)
    }

    abstract fun getParentPsiFunctionCall(element: PsiElement): PsiElement?

    private fun getTopParentPsiFunctionCall(element: PsiElement): PsiElement {
        var current = element
        var parent = getParentPsiFunctionCall(element)

        while (parent != null) {
            current = parent
            parent = getParentPsiFunctionCall(parent)
        }

        return current
    }

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
                    val isNextArgExplicit = if (i != indices.size - 1) (argsText[i + 1].first() != '{') else false
                    append(rewriteArg(arg, arg.first() == '{', isNextArgExplicit) + " ")
                }
            }
        }.replace("\\s+".toRegex(), " ")
    }

    abstract fun createPsiFromText(expr: String, psiFunctionCall: PsiElement): PsiElement?

    private fun getArgumentIndex(element: PsiElement): Int {
        var i = 0
        for (arg in element.parent.children) {
            when (arg) {
                is ArendNameTele, is ArendFieldTele, is ArendTypeTele -> {
                    if (arg.equals(element)) {
                        return i
                    }

                    for (tok in arg.text.split("\\s+".toRegex())) {
                        if (tok == ":") break
                        i++
                    }
                }
            }
        }
        return -1
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
    override fun getParentPsiFunctionCall(element: PsiElement): PsiElement? {
        val parent = element.parent?.ancestor<ArendArgumentAppExpr>()
        parent ?: return null
        return if (parent.argumentList.contains(element)) element else parent
    }

    override fun getCallingParameters(element: PsiElement): List<String> {
        val psiFunctionCall = element as ArendArgumentAppExpr
        return psiFunctionCall.argumentList.map { it.text }
    }

    override fun createPsiFromText(expr: String, psiFunctionCall: PsiElement): PsiElement? {
        val factory = ArendPsiFactory(psiFunctionCall.project)
        return factory.createExpression(expr)
            .childOfType<ArendArgumentAppExpr>()
    }
}

class SwitchParamImplicitnessTypeApplier : SwitchParamImplicitnessApplier() {
    override fun getParentPsiFunctionCall(element: PsiElement): PsiElement? {
        TODO("Not yet implemented")
    }

    override fun getCallingParameters(element: PsiElement): List<String> {
        TODO()
//        val psiFunctionCall = element as ArendLocalCoClauseImpl
//        return psiFunctionCall.lamParamList.map { it.text }
    }

    override fun createPsiFromText(expr: String, psiFunctionCall: PsiElement): PsiElement {
        TODO()
//        val factory = ArendPsiFactory(project)
//        val body = (psiFunctionCall as ArendLocalCoClause).expr?.text
//        return factory.createLocalCoClause(expr, body)
    }
}
