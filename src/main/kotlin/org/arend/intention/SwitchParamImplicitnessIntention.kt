package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.arend.codeInsight.ArendParameterInfoHandler
import org.arend.error.DummyErrorReporter
import org.arend.ext.module.LongName
import org.arend.ext.variable.Variable
import org.arend.ext.variable.VariableImpl
import org.arend.naming.renamer.StringRenamer
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.util.ArendBundle
import org.arend.psi.ext.PsiReferable
import org.arend.refactoring.getTele
import org.arend.refactoring.splitTele
import org.arend.resolving.ArendIdReferableConverter
import org.arend.term.abs.Abstract
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.concrete.Concrete
import org.arend.typechecking.visitor.SyntacticDesugarVisitor

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
        val elementOnCaret = element.containingFile.findElementAt(editor.caretModel.offset)
        val switchedArgIndexInTele = getSwitchedArgIndex(element, elementOnCaret)

        // switch all variables in telescope
        if (switchedArgIndexInTele == null || switchedArgIndexInTele == -1) {
            switchTeleExplicitness(element)
        } else {
            chooseApplier(element)?.applyTo(element, switchedArgIndexInTele)
        }
    }

    private fun chooseApplier(element: ArendCompositeElement): SwitchParamImplicitnessApplier? {
        return when (element) {
            is ArendNameTele, is ArendFieldTele -> SwitchParamImplicitnessNameFieldApplier()
            is ArendTypeTele -> SwitchParamImplicitnessTypeApplier()
            else -> null
        }
    }

    private fun getSwitchedArgIndex(tele: ArendCompositeElement, switchedArg: PsiElement?): Int? {
        switchedArg ?: return -1
        val argsText = getTele(tele)?.map { it.text }
        return if (argsText?.size == 1) 0 else argsText?.indexOf(switchedArg.text)
    }

    private fun switchTeleExplicitness(tele: ArendCompositeElement) {
        val psiFunctionDef = tele.ancestor<PsiReferable>() as PsiElement
        val teleIndex = psiFunctionDef.children.indexOf(tele)
        val anchor = psiFunctionDef.children[teleIndex - 1]
        val factory = ArendPsiFactory(tele.project)

        val newTele = createSwitchedTele(factory, tele)
        newTele ?: return

        var curElement = tele
        val teleSize = getTele(tele)?.size ?: return
        for (i in 0..teleSize) {
            val splittedTele = chooseApplier(curElement)?.applyTo(curElement, 0)
            splittedTele ?: continue
            curElement = splittedTele.nextSibling as? ArendCompositeElement ?: break
        }

        val first = psiFunctionDef.children[teleIndex]
        val last = psiFunctionDef.children[teleIndex + teleSize - 1]

        psiFunctionDef.deleteChildRangeWithNotification(first, last)
        val inserted = psiFunctionDef.addAfterWithNotification(newTele, anchor)
        val psiWs = factory.createWhitespace(" ")
        psiFunctionDef.addBeforeWithNotification(psiWs, inserted)
    }
}

abstract class SwitchParamImplicitnessApplier {
    protected lateinit var factory: ArendPsiFactory
    private val processed = mutableSetOf<String>()

    fun applyTo(element: ArendCompositeElement, switchedArgIndexInTele: Int): PsiElement {
        factory = ArendPsiFactory(element.project)

        val psiFunctionDef = element.ancestor<PsiReferable>() as? PsiElement ?: return element
        val switchedArgIndexInDef = getTeleIndexInDef(psiFunctionDef, element) + switchedArgIndexInTele

        for (psiReference in ReferencesSearch.search(psiFunctionDef)) {
            val psiFunctionUsage = psiReference.element
            replaceWithSubTerms(psiFunctionUsage, psiFunctionDef, switchedArgIndexInDef)
        }

        return rewriteFunctionDef(element, switchedArgIndexInTele)
    }

    /*
        Initially, this function replaces all children of given element `psiFunctionUsage`,
        which contains references to `psiFunctionDef` and
        then converts the current to the prefix form and rewrites this
    */
    private fun replaceWithSubTerms(
        psiFunctionUsage: PsiElement,
        psiFunctionDef: PsiElement,
        switchedArgIndexInDef: Int,
    ) {
        // this element has already been replaced
        if (!psiFunctionUsage.isValid) return

        val psiFunctionCall = getParentPsiFunctionCall(psiFunctionUsage)
        val psiFunctionCallPrefix = convertFunctionCallToPrefix(psiFunctionCall) ?: psiFunctionCall

        // TODO: don't compare strings
        if (processed.contains(psiFunctionCall.text)) {
            return
        }

        val replacedFunctionCall = psiFunctionCall.replaceWithNotification(psiFunctionCallPrefix)
        tryProcessPartialUsageWithoutArguments(replacedFunctionCall, psiFunctionDef)

        val refs = searchRefsInPsiElement(psiFunctionDef, replacedFunctionCall)
        if (refs.isEmpty()) {
            return
        }

        val callerRef = extractRefIdFromCalling(psiFunctionDef, replacedFunctionCall)
        for (ref in refs) {
            if (ref.element != callerRef) {
                replaceWithSubTerms(ref.element, psiFunctionDef, switchedArgIndexInDef)
            }
        }

        // avoid case when another infix operator on the top
        if (psiFunctionDef == resolveCaller(replacedFunctionCall)) {
            val newPsiElement = rewriteFunctionCalling(
                psiFunctionUsage,
                replacedFunctionCall,
                psiFunctionDef,
                switchedArgIndexInDef
            )

            val psiToBeReplace =
                if (newPsiElement is ArendLamExpr) {
                    replacedFunctionCall.ancestor<ArendNewExpr>() ?: replacedFunctionCall
                } else replacedFunctionCall

            val replacedNewFunctionCall = psiToBeReplace.replaceWithNotification(newPsiElement)
            processed.add(replacedNewFunctionCall.text)
        }
    }

    private fun getParametersIndices(psiFunctionDef: PsiElement, psiFunctionCall: PsiElement): List<Int> {
        val parameterHandler = ArendParameterInfoHandler()
        val parameters = parameterHandler.getAllParametersForReferable(psiFunctionDef as PsiReferable)

        // Check this
        val argsExplicitness = getCallingParametersWithPhantom(psiFunctionCall).map { it.first() != '{' }
        val argsIndices = mutableListOf<Int>()

        for (i in argsExplicitness.indices) {
            argsIndices.add(parameterHandler.findParamIndex(parameters, argsExplicitness.subList(0, i + 1)))
        }

        val cntPhantomArgs = argsExplicitness.size - getCallingParameters(psiFunctionCall).size
        return argsIndices.subList(cntPhantomArgs, argsIndices.size)
    }

    private fun rewriteFunctionDef(tele: ArendCompositeElement, switchedArgIndexInTele: Int): PsiElement {
        val teleSize = getTele(tele)?.size
        if (teleSize != null && teleSize > 1) {
            splitTele(tele, switchedArgIndexInTele)
        }

        val newTele = createSwitchedTele(factory, tele)
        newTele ?: return tele

        return tele.replaceWithNotification(newTele)
    }

    private fun rewriteFunctionCalling(
        psiFunctionUsage: PsiElement,
        psiFunctionCall: PsiElement,
        psiFunctionDef: PsiElement,
        argumentIndex: Int
    ): PsiElement {
        val functionName = psiFunctionUsage.text.replace("`", "")
        val argsText = getCallingParameters(psiFunctionCall).map { it.text } as MutableList<String>
        val indices = getParametersIndices(psiFunctionDef, psiFunctionCall) as MutableList<Int>
        val lastIndex = indices.maxOrNull() ?: -1
        val needRewriteToLambda = (lastIndex < argumentIndex)

        if (needRewriteToLambda) {
            /////////////////////////////
            val teleExplicitness = mutableListOf<Boolean>()
            for (tele in getTelesFromDef(psiFunctionDef)) {
                for (_param in getTele(tele)!!) {
                    teleExplicitness.add(tele.text.first() != '{')
                }
            }

            val omittedParametersExplicitness = teleExplicitness.subList(lastIndex + 1, teleExplicitness.size)
            val allOmitted = omittedParametersExplicitness.all { !it }

            if (allOmitted) {
                return createPsiFromText("${psiFunctionCall.text} _", psiFunctionCall)
            }
            /////////////////////////////
            return rewriteToLambda(psiFunctionCall, psiFunctionDef, argumentIndex, lastIndex)
        }

        val existsInArgList = (indices.indexOf(argumentIndex) != -1)
        if (!existsInArgList) {
            val insertAfterIndex = -indices.binarySearch(argumentIndex) - 1
            argsText.add(insertAfterIndex, "{_}")
            indices.add(insertAfterIndex, argumentIndex)
        }

        val elementIndexInArgs = indices.indexOf(argumentIndex)
        val expr = buildFunctionCallingText(functionName, argsText, indices, argumentIndex)

        val newPsiFunctionCall = createPsiFromText(expr.trimEnd(), psiFunctionCall)
        val newPsiSwitchedArgument = getCallingParameters(newPsiFunctionCall)[elementIndexInArgs]

        if (!existsInArgList) {
            val anchor = getCallingParameters(psiFunctionCall)[elementIndexInArgs]
            psiFunctionCall.addBeforeWithNotification(newPsiSwitchedArgument, anchor)

            val psiWs = factory.createWhitespace(" ")
            val anchorInsertedArg = getCallingParameters(psiFunctionCall)[elementIndexInArgs]
            psiFunctionCall.addAfterWithNotification(psiWs, anchorInsertedArg)
        } else {
            val isNextArgExplicit =
                if (elementIndexInArgs != indices.size - 1) (argsText[elementIndexInArgs + 1].first() != '{') else true
            val needToRemoveArg = (argsText[elementIndexInArgs] == "_") && isNextArgExplicit
            var psiSwitchedArg = getCallingParameters(psiFunctionCall)[elementIndexInArgs]

            if (needToRemoveArg) {
                val prevWs = psiSwitchedArg.prevSibling
                prevWs?.deleteWithNotification()
                psiSwitchedArg.deleteWithNotification()
            } else {
                // from explicit to implicit
                // need to add all omitted previous implicit args
                psiSwitchedArg = psiSwitchedArg.replaceWithNotification(newPsiSwitchedArgument)

                if (newPsiSwitchedArgument.text.first() == '{') {
                    val paramsBefore =
                        if (elementIndexInArgs == 0) indices[elementIndexInArgs] else (indices[elementIndexInArgs] - indices[elementIndexInArgs - 1] - 1)

                    if (paramsBefore > 0) {
                        addArgumentSequenceBefore(factory, "{_} ".repeat(paramsBefore), psiFunctionCall, psiSwitchedArg)
                    }
                }
            }
        }
        return psiFunctionCall
    }

    private fun rewriteToLambda(
        psiFunctionCall: PsiElement,
        psiFunctionDef: PsiElement,
        switchedArgIndex: Int,
        startFromIndex: Int
    ): ArendLamExpr {
        val teleList = mutableListOf<String>()
        val referables = getContext(psiFunctionCall) as MutableList

        for (tele in getTelesFromDef(psiFunctionDef)) {
            val paramsInTele = getTele(tele) ?: continue
            teleList.addAll(paramsInTele.map {
                val variable = VariableImpl(it.text)
                val freshName = StringRenamer().generateFreshName(variable, referables)
                referables.add(VariableImpl(freshName))
                if (tele.text.first() != '{') freshName else "{$freshName}"
            })
        }

        val teleListCut = teleList.subList(startFromIndex + 1, teleList.size)
        val callingArgs = teleList.toMutableList()
        callingArgs[switchedArgIndex] = rewriteArg(callingArgs[switchedArgIndex], false)

        val callingArgsCut = callingArgs.subList(startFromIndex + 1, callingArgs.size)
        val newFunctionCallText = psiFunctionCall.text + callingArgsCut.joinToString(" ", " ")
        return factory.createLam(teleListCut, newFunctionCallText)
    }

    /*
        wrap the function, when its in arguments, in parens.
        case:
        \func suc ({-caret-}a : Nat) => a Nat.+ 1
        \func foo (f : (Nat -> Nat) -> Nat) => f suc

        converts to:
        \func suc ({-caret-}a : Nat) => a Nat.+ 1
        \func foo (f : (Nat -> Nat) -> Nat) => f (suc)
    */
    private fun tryProcessPartialUsageWithoutArguments(
        psiFunctionCall: PsiElement,
        psiFunctionDef: PsiElement
    ) {
        val parameters = getCallingParameters(psiFunctionCall)
        for ((i, parameter) in parameters.withIndex()) {
            if (parameter.text == "_" || parameter.text.contains(" ")) continue
            val paramRef = parameter.childOfType<ArendLongName>() ?: parameter.childOfType<ArendRefIdentifier>()
            ?: continue

            val resolved = if (paramRef is ArendLongName) {
                getRefToFunFromLongName(paramRef)
            } else (paramRef as ArendRefIdentifier).resolve

            if (resolved == psiFunctionDef) {
                val argumentInBraces = factory.createArgument("(${parameter.text})")
                val ithArg = getCallingParameters(psiFunctionCall)[i]
                ithArg.replaceWithNotification(argumentInBraces)
            }
        }
    }

    abstract fun getParentPsiFunctionCall(element: PsiElement): PsiElement

    abstract fun convertFunctionCallToPrefix(psiFunctionCall: PsiElement): PsiElement?

    abstract fun getCallingParameters(element: PsiElement): List<PsiElement>

    abstract fun resolveCaller(element: PsiElement): PsiElement?

    abstract fun createPsiFromText(expr: String, psiFunctionCall: PsiElement): PsiElement

    abstract fun getContext(element: PsiElement): List<Variable>

    abstract fun extractRefIdFromCalling(psiFunctionDef: PsiElement, psiFunctionCall: PsiElement): PsiElement?

    abstract fun getCallingParametersWithPhantom(psiFunctionCall: PsiElement): List<String>
}

class SwitchParamImplicitnessNameFieldApplier : SwitchParamImplicitnessApplier() {
    override fun getParentPsiFunctionCall(element: PsiElement): PsiElement =
        element.parent?.ancestor<ArendArgumentAppExpr>() ?: element

    override fun convertFunctionCallToPrefix(psiFunctionCall: PsiElement): PsiElement? {
        fun buildPrefixTextFromConcrete(concrete: Concrete.AppExpression): String {
            return buildString {
                val psiFunction = concrete.function.data as PsiElement
                val functionText = psiFunction.text.replace("`", "")
                append("$functionText ")
                for (arg in concrete.arguments) {
                    val concreteArg = arg.expression
                    val argText =
                        if (concreteArg !is Concrete.AppExpression) {
                            (concreteArg.data as PsiElement).text
                        } else buildPrefixTextFromConcrete(concreteArg)

                    // avoid duplication in case R.foo <=> foo {R}
                    // functionText is `R.foo`, argText is `R.foo`
                    if (functionText == argText) {
                        continue
                    }

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
            }.trimEnd()
        }

        val concrete = convertCallToConcrete(psiFunctionCall) ?: return null
        val functionCallText = buildPrefixTextFromConcrete(concrete)
        return createPsiFromText(functionCallText, psiFunctionCall)
    }

    override fun getCallingParameters(element: PsiElement): List<PsiElement> {
        val psiFunctionCall = element as ArendArgumentAppExpr
        return psiFunctionCall.argumentList
    }

    override fun createPsiFromText(expr: String, psiFunctionCall: PsiElement): PsiElement {
        return factory.createExpression(expr).childOfType<ArendArgumentAppExpr>()!!
    }

    override fun getContext(element: PsiElement): List<Variable> {
        val argumentAppExpr = element as ArendArgumentAppExpr
        return argumentAppExpr.scope.elements.map { VariableImpl(it.textRepresentation()) }
    }

    override fun extractRefIdFromCalling(psiFunctionDef: PsiElement, psiFunctionCall: PsiElement): PsiElement? {
        val function = (psiFunctionCall as ArendArgumentAppExpr).atomFieldsAcc!!
        val refs = searchRefsInPsiElement(psiFunctionDef, function)
        return if (refs.isEmpty()) null else refs.first().element
    }

    override fun getCallingParametersWithPhantom(psiFunctionCall: PsiElement): List<String> {
        val concrete = convertCallToConcrete(psiFunctionCall)
        return concrete?.arguments?.map { if (it.isExplicit) it.toString() else "{$it}" } ?: emptyList()
    }

    override fun resolveCaller(element: PsiElement): PsiElement? {
        val psiFunctionCall = element as ArendArgumentAppExpr
        val longName = psiFunctionCall.atomFieldsAcc?.childOfType<ArendLongName>()
        longName ?: return null
        return getRefToFunFromLongName(longName)
    }

    private fun convertCallToConcrete(psiFunctionCall: PsiElement): Concrete.AppExpression? {
        val scope = (psiFunctionCall as ArendArgumentAppExpr).scope

        return ConcreteBuilder.convertExpression(psiFunctionCall as Abstract.Expression)
            .accept(
                ExpressionResolveNameVisitor(
                    ArendIdReferableConverter,
                    scope,
                    ArrayList(),
                    DummyErrorReporter.INSTANCE,
                    null
                ), null
            )
            .accept(SyntacticDesugarVisitor(DummyErrorReporter.INSTANCE), null)
                as? Concrete.AppExpression ?: return null
    }
}

class SwitchParamImplicitnessTypeApplier : SwitchParamImplicitnessApplier() {
    override fun getParentPsiFunctionCall(element: PsiElement): PsiElement {
        return element.parent?.ancestor<ArendLocalCoClause>() ?: element
    }

    override fun convertFunctionCallToPrefix(psiFunctionCall: PsiElement): PsiElement = psiFunctionCall

    override fun getCallingParameters(element: PsiElement): List<PsiElement> {
        val psiFunctionCall = element as ArendLocalCoClause
        return psiFunctionCall.lamParamList
    }

    override fun createPsiFromText(expr: String, psiFunctionCall: PsiElement): PsiElement {
        val body = (psiFunctionCall as ArendLocalCoClause).expr?.text
        return factory.createLocalCoClause(expr, body)
    }

    override fun getContext(element: PsiElement): List<Variable> {
        val localCoClause = element as ArendLocalCoClause
        return localCoClause.scope.elements.map { VariableImpl(it.textRepresentation()) }
    }

    override fun extractRefIdFromCalling(psiFunctionDef: PsiElement, psiFunctionCall: PsiElement): PsiElement? {
        val function = (psiFunctionCall as ArendLocalCoClause).longName!!
        val refs = searchRefsInPsiElement(psiFunctionDef, function)
        return if (refs.isEmpty()) null else refs.first().element
    }

    override fun getCallingParametersWithPhantom(psiFunctionCall: PsiElement): List<String> {
        return getCallingParameters(psiFunctionCall).map { it.text }
    }

    override fun resolveCaller(element: PsiElement): PsiElement? {
        val coClause = element as ArendLocalCoClause
        val longName = coClause.longName
        longName ?: return null
        return getRefToFunFromLongName(longName)
    }
}

private fun createSwitchedTele(factory: ArendPsiFactory, tele: ArendCompositeElement): ArendCompositeElement? {
    val isExplicit = (tele.text.first() == '(')
    val params = getTele(tele)?.joinToString(" ") { it.text }

    return when (tele) {
        is ArendNameTele -> {
            val type = tele.expr!!.text
            factory.createNameTele(params, type, !isExplicit)
        }

        is ArendFieldTele -> {
            val type = tele.expr!!.text
            factory.createNameTele(params, type, !isExplicit)
        }

        is ArendTypeTele -> {
            val typedExpr = tele.typedExpr!!
            val expr = typedExpr.expr
            if (expr == null) {
                factory.createTypeTele(null, typedExpr.text, !isExplicit)
            } else {
                factory.createTypeTele(params, expr.text, !isExplicit)
            }
        }
        else -> null
    }
}

private fun rewriteArg(text: String, isNextArgExplicit: Boolean): String {
    var newText: String
    val toExplicit = (text.first() == '{')

    if (toExplicit) {
        newText = text.substring(1, text.length - 1)
        if (needToWrapInBrackets(text)) {
            newText = "($newText)"
        }
        return newText
    }

    if (text == "_" && isNextArgExplicit) {
        return ""
    }

    return "{$text}"
}

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
                append(rewriteArg(arg, isNextArgExplicit) + " ")
            }
        }
    }.replace("\\s+".toRegex(), " ")
}

private fun getTeleIndexInDef(def: PsiElement, tele: PsiElement): Int {
    val parameterHandler = ArendParameterInfoHandler()
    val parameters = parameterHandler.getAllParametersForReferable(def as PsiReferable)

    var i = 0
    for (parameter in parameters) {
        if (parameter == tele) return i
        val teles = getTele(parameter as PsiElement)
        teles ?: continue
        i += teles.size
    }

    return -1
}

private fun addArgumentSequenceBefore(
    factory: ArendPsiFactory,
    argSequence: String,
    psiFunctionCall: PsiElement,
    anchor: PsiElement
) {
    val exprPsi = factory.createExpression("dummy $argSequence").childOfType<ArendArgumentAppExpr>()!!
    val (first, last) = Pair(exprPsi.argumentList.first(), exprPsi.argumentList.last())

    val psiWs = factory.createWhitespace(" ")
    psiFunctionCall.addRangeBeforeWithNotification(first, last, anchor)
    psiFunctionCall.addBeforeWithNotification(psiWs, anchor)
}

/*
    Returns list of all references to `def` in `element` scope
*/
private fun searchRefsInPsiElement(def: PsiElement, element: PsiElement): List<PsiReference> {
    val scope = LocalSearchScope(element)
    return ReferencesSearch.search(def, scope).findAll().toList()
}

private fun getRefToFunFromLongName(longName: ArendLongName): PsiElement? {
    val ref = longName.children.last() as? ArendRefIdentifier
    return ref?.resolve
}

private fun ArendPsiFactory.createArgument(arg: String): PsiElement =
    createExpression("dummy $arg").childOfType<ArendArgumentAppExpr>()?.argumentList?.first()
        ?: error("Failed to create argument ")

private fun needToWrapInBrackets(expr: String): Boolean {
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