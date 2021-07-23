package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.arend.codeInsight.ArendParameterInfoHandler
import org.arend.error.DummyErrorReporter
import org.arend.naming.reference.Referable
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.util.ArendBundle
import org.arend.psi.ext.PsiReferable
import org.arend.psi.impl.ArendLocalCoClauseImpl
import org.arend.refactoring.getTele
import org.arend.refactoring.splitTele
import org.arend.resolving.ArendIdReferableConverter
import org.arend.term.abs.Abstract
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.concrete.Concrete
import org.arend.typechecking.visitor.SyntacticDesugarVisitor
import kotlin.math.abs

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
        val argsText = getTele(tele)!!.map { it.text }
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
    private val processed = mutableSetOf<String>()

    fun applyTo(element: ArendCompositeElement, switchedArgIndexInTele: Int) {
        val psiFunctionDef = element.ancestor<PsiReferable>() as? PsiElement
        psiFunctionDef ?: return

        val switchedArgIndexInDef = getArgumentIndex(element) + switchedArgIndexInTele

        for (psiReference in ReferencesSearch.search(psiFunctionDef)) {
            val psiFunctionUsage = psiReference.element
            replaceDeep(psiFunctionUsage, psiFunctionDef, switchedArgIndexInDef)
        }

        rewriteFunctionDef(element, switchedArgIndexInTele)
    }

    /*
        Initially, this function replaces all children of given element `psiFunctionUsage`
        then converts the current to the prefix form and rewrites this
    */
    private fun replaceDeep(
        psiFunctionUsage: PsiElement,
        psiFunctionDef: PsiElement,
        switchedArgIndexInDef: Int,
    ) {
        // this element has already been replaced
        if (!psiFunctionUsage.isValid) return

        val psiFunctionCall = getTopParentPsiFunctionCall(psiFunctionUsage)
        val psiFunctionCallPrefix = convertFunctionCallToPrefix(psiFunctionCall)
        psiFunctionCallPrefix ?: return

        // TODO: don't compare strings
        if (processed.contains(psiFunctionCall.text)) {
            return
        }

        val replacedFunctionCall = psiFunctionCall.replaceWithNotification(psiFunctionCallPrefix)
        val scope = LocalSearchScope(replacedFunctionCall)
        val refs = ReferencesSearch.search(psiFunctionDef, scope)

        if (refs.findAll().isEmpty()) {
            return
        }

        for (ref in refs) {
            val curElement = ref.element
            // TODO: find another way to check this
            if (abs(curElement.textOffset - replacedFunctionCall.textOffset) < 3) {
                continue
            }
            replaceDeep(curElement, psiFunctionDef, switchedArgIndexInDef)
        }

        val newPsiElement = rewriteFunctionCalling(
            psiFunctionUsage,
            replacedFunctionCall,
            psiFunctionDef,
            switchedArgIndexInDef
        )
        val replacedNewFunctionCall = replacedFunctionCall.replaceWithNotification(newPsiElement)
        processed.add(replacedNewFunctionCall.text)
    }

    private fun getParametersIndices(psiFunctionUsage: PsiElement, psiFunctionCall: PsiElement): List<Int> {
        val parameterHandler = ArendParameterInfoHandler()
        val container =
            parameterHandler.extractRefFromSourceNode(if (psiFunctionUsage !is ArendIPName) psiFunctionUsage as Abstract.SourceNode else psiFunctionUsage.ancestor<ArendLiteral>() as Abstract.SourceNode)
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
        val text = with(tele.text) { substring(1, length - 1) }
        val (params, type) = text.split(":")
        val newTele = factory.createNameTele(params.trim().trimEnd(), type.trim().trimEnd(), !isExplicit)

        tele.replaceWithNotification(newTele)
    }

    private fun rewriteFunctionCalling(
        psiFunctionUsage: PsiElement,
        psiFunctionCall: PsiElement,
        psiFunctionDef: PsiElement,
        argumentIndex: Int
    ): PsiElement {
        val functionName = psiFunctionUsage.text.replace("`", "")
        val parameters = getCallingParameters(psiFunctionCall)
        val parameterIndices = getParametersIndices(psiFunctionUsage, psiFunctionCall)
        val lastIndex = parameterIndices.maxOrNull() ?: -1
        val isPartialAppExpr = (lastIndex < argumentIndex)

        if (isPartialAppExpr) {
            return rewriteToLambda(psiFunctionUsage, psiFunctionCall, psiFunctionDef, argumentIndex, lastIndex)
        }

        val argsText = parameters as MutableList<String>
        val indices: MutableList<Int> = parameterIndices as MutableList<Int>
        val existsInArgList = (parameterIndices.indexOf(argumentIndex) != -1)

        if (!existsInArgList) {
            val insertAfterIndex = -parameterIndices.binarySearch(argumentIndex) - 1
            argsText.add(insertAfterIndex, "{_}")
            indices.add(insertAfterIndex, argumentIndex)
        }

        val elementIndexInArgs = indices.indexOf(argumentIndex)
        val expr = buildFunctionCallingText(functionName, argsText, indices, argumentIndex)

        val newPsiFunctionCall = createPsiFromText(expr.trimEnd(), psiFunctionCall)
        val newPsiSwitchedArgument = getIthPsiCallingParameter(newPsiFunctionCall, elementIndexInArgs)

        if (!existsInArgList) {
            val anchor = getIthPsiCallingParameter(psiFunctionCall, elementIndexInArgs)
            psiFunctionCall.addBeforeWithNotification(newPsiSwitchedArgument, anchor)

            val factory = ArendPsiFactory(psiFunctionUsage.project)
            val psiWs = factory.createWhitespace(" ")
            val anchorInsertedArg = getIthPsiCallingParameter(psiFunctionCall, elementIndexInArgs)
            psiFunctionCall.addAfterWithNotification(psiWs, anchorInsertedArg)
        } else {
            val isNextArgExplicit =
                if (elementIndexInArgs != indices.size - 1) (argsText[elementIndexInArgs + 1].first() != '{') else false
            val needToRemoveArg = (argsText[elementIndexInArgs] == "_") && isNextArgExplicit
            val psiSwitchedArg = getIthPsiCallingParameter(psiFunctionCall, elementIndexInArgs)

            if (needToRemoveArg) {
                val nextWs = psiSwitchedArg.nextSibling
                nextWs.deleteWithNotification()
                psiSwitchedArg.deleteWithNotification()
            } else {
                psiSwitchedArg.replaceWithNotification(newPsiSwitchedArgument)
            }
        }
        return psiFunctionCall
    }

    private fun rewriteToLambda(
        psiFunctionUsage: PsiElement,
        psiFunctionCall: PsiElement,
        psiFunctionDef: PsiElement,
        switchedArgIndex: Int,
        startFromIndex: Int
    ): PsiElement {
        val teleList = mutableListOf<String>()

        for (tele in getTelesFromDef(psiFunctionDef)) {
            val paramsInTele = getTele(tele) ?: continue
            teleList.addAll(paramsInTele.map {
                val genName = "gen_" + it.text
                if (tele.text.first() != '{') genName else "{$genName}"
            })
        }

        val teleListCut = teleList.subList(startFromIndex + 1, teleList.size)
        val callingArgs = teleList.toMutableList()
        val toExplicit = (callingArgs[switchedArgIndex].first() == '{')
        callingArgs[switchedArgIndex] = rewriteArg(callingArgs[switchedArgIndex], toExplicit, false)

        val callingArgsCut = callingArgs.subList(startFromIndex + 1, callingArgs.size)
        val newFunctionCallText = psiFunctionCall.text + callingArgsCut.joinToString(" ", " ")
        val factory = ArendPsiFactory(psiFunctionUsage.project)
        return factory.createLam(teleListCut, newFunctionCallText)
    }

    abstract fun getParentPsiFunctionCall(element: PsiElement): PsiElement?

    abstract fun convertFunctionCallToPrefix(psiFunctionCall: PsiElement): PsiElement?

    private fun getTopParentPsiFunctionCall(element: PsiElement): PsiElement {
        var current = element
        var parent = getParentPsiFunctionCall(element)

        while (parent != null) {
            current = parent
            parent = getParentPsiFunctionCall(parent)
        }

        return current
    }

    abstract fun getIthPsiCallingParameter(element: PsiElement, index: Int): PsiElement

    abstract fun getCallingParameters(element: PsiElement): List<String>

    // remove this function?
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

    abstract fun createPsiFromText(expr: String, psiFunctionCall: PsiElement): PsiElement

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

        if (element !is ArendArgumentAppExpr) return parent

        val concreteFunctionCall = convertFunctionCallToPrefix(parent) as? ArendArgumentAppExpr ?: return null
        for (arg in concreteFunctionCall.argumentList) {
            if (arg.text.equals(element.text) || arg.text.equals("(" + element.text + ")"))
                return null
        }
        return parent
    }

    override fun convertFunctionCallToPrefix(psiFunctionCall: PsiElement): PsiElement? {
        fun needToWrapInBrackets(expr: String): Boolean {
            val stack = ArrayDeque<Char>()
            for (sym in expr) {
                when (sym) {
                    '(' -> stack.addFirst(sym)
                    ')' -> stack.removeFirst()
                    ' ' -> if (stack.isEmpty()) return true
                }
            }
            return false
        }

        fun buildTextFromConcrete(concrete: Concrete.AppExpression): String {
            return buildString {
                append("${concrete.function} ")
                for (arg in concrete.arguments) {
                    val argText = arg.expression.toString()
                    if (arg.isExplicit) {
                        if (needToWrapInBrackets(argText)) {
                            append("($argText) ")
                        } else {
                            append("$argText ")
                        }
                    } else {
                        append("{$argText} ")
                    }
                }
            }
        }

        val scope = (psiFunctionCall as ArendArgumentAppExpr).scope
        val concrete = ConcreteBuilder.convertExpression(psiFunctionCall as Abstract.Expression)
            .accept(
                ExpressionResolveNameVisitor(
                    ArendIdReferableConverter,
                    scope,
                    ArrayList<Referable>(),
                    DummyErrorReporter.INSTANCE,
                    null
                ), null
            )
            .accept(SyntacticDesugarVisitor(DummyErrorReporter.INSTANCE), null)
                as? Concrete.AppExpression ?: return null

        val functionCallText = buildTextFromConcrete(concrete)
        return createPsiFromText(functionCallText, psiFunctionCall)
    }

    override fun getIthPsiCallingParameter(element: PsiElement, index: Int): PsiElement {
        val psiFunctionCall = element as ArendArgumentAppExpr
        return psiFunctionCall.argumentList[index]
    }

    override fun getCallingParameters(element: PsiElement): List<String> {
        val psiFunctionCall = element as ArendArgumentAppExpr
        return psiFunctionCall.argumentList.map { it.text }
    }

    override fun createPsiFromText(expr: String, psiFunctionCall: PsiElement): PsiElement {
        val factory = ArendPsiFactory(psiFunctionCall.project)
        return factory.createExpression(expr).childOfType<ArendArgumentAppExpr>()!!
    }
}

class SwitchParamImplicitnessTypeApplier : SwitchParamImplicitnessApplier() {
    // TODO: check this case
    override fun getParentPsiFunctionCall(element: PsiElement): PsiElement? {
        return element.parent?.ancestor<ArendLocalCoClause>()
    }

    override fun convertFunctionCallToPrefix(psiFunctionCall: PsiElement): PsiElement {
        return psiFunctionCall
    }

    override fun getIthPsiCallingParameter(element: PsiElement, index: Int): PsiElement {
        val psiFunctionCall = element as ArendLocalCoClause
        return psiFunctionCall.lamParamList[index]
    }

    override fun getCallingParameters(element: PsiElement): List<String> {
        val psiFunctionCall = element as ArendLocalCoClauseImpl
        return psiFunctionCall.lamParamList.map { it.text }
    }

    override fun createPsiFromText(expr: String, psiFunctionCall: PsiElement): PsiElement {
        val factory = ArendPsiFactory(psiFunctionCall.project)
        val body = (psiFunctionCall as ArendLocalCoClause).expr?.text
        return factory.createLocalCoClause(expr, body)
    }
}
