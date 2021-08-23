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
import org.arend.naming.reference.Referable
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
    private val wrapped = mutableSetOf<PsiElement>()

    fun applyTo(element: ArendCompositeElement, switchedArgIndexInTele: Int): PsiElement {
        factory = ArendPsiFactory(element.project)

        val def = element.ancestor<PsiReferable>() as? PsiElement ?: return element
        val switchedArgIndexInDef = getTeleIndexInDef(def, element) + switchedArgIndexInTele

        if (element !is ArendTypeTele) {
            for (ref in ReferencesSearch.search(def)) {
                val usage = ref.element
                if (!usage.isValid) continue
                val call = getParentPsiFunctionCall(usage)
                val concrete = convertCallToConcrete(call) ?: continue
                wrapCallIntoParens(concrete, def)
            }
        }

        for (ref in ReferencesSearch.search(def)) {
            replaceWithSubTerms(ref.element, def, switchedArgIndexInDef)
        }

        return rewriteFunctionDef(element, switchedArgIndexInTele)
    }

    private fun wrapCallIntoParens(concreteAppExpr: Concrete.AppExpression, def: PsiElement): PsiElement? {
        var concrete = concreteAppExpr
        val cntArguments = concrete.arguments.size

        // It's important to update concrete, because its `data` becomes dummy after rewriting
        for (i in 0 until cntArguments) {
            val argument = concrete.arguments[i].expression
            if (argument is Concrete.AppExpression) {
                val parentCallWithWrappedArgument = wrapCallIntoParens(argument, def) ?: continue
                concrete = convertCallToConcrete(parentCallWithWrappedArgument) ?: continue
            }
        }

        val resolve = tryResolveFunctionName(concrete.function.data as PsiElement)

        if (def == resolve) {
            val argumentsSequence = concrete.argumentsSequence.map { it.expression.data as PsiElement }
            val first = argumentsSequence.minByOrNull { it.textOffset } ?: return null
            val last = argumentsSequence.maxByOrNull { it.textOffset } ?: return null

            val call = getParentPsiFunctionCall(first)
            if (wrapped.contains(call)) return null

            val firstChild = getTopChildOnPath(first, call)
            val lastChild = getTopChildOnPath(last, call)

            val callText = buildPrefixTextFromConcrete(concrete)
            val newCall = buildString {
                for (child in call.children) {
                    when (child) {
                        firstChild -> {
                            append("($callText) ")
                            break
                        }
                        else -> {
                            append("${child.text} ")
                        }
                    }
                }
            }.trimEnd()

            val wrappedCall = factory.createExpression(newCall).childOfType<ArendArgumentAppExpr>()!!.children.last()
            val insertedCall = call.addAfterWithNotification(wrappedCall, lastChild)
            call.deleteChildRangeWithNotification(firstChild, lastChild)

            wrapped.add(insertedCall.childOfType<ArendArgumentAppExpr>()!!)
            return call
        }

        return null
    }

    /*
        Initially, this function replaces all children of given element `usage`,
        which contains references to `def` and
        then converts the current to the prefix form and rewrites this
    */
    private fun replaceWithSubTerms(
        usage: PsiElement,
        def: PsiElement,
        switchedArgIndexInDef: Int,
    ) {
        // this element has already been replaced
        if (!usage.isValid) return

        val call = getParentPsiFunctionCall(usage)
        val callPrefix = convertFunctionCallToPrefix(call) ?: call
        val needUnwrap = wrapped.contains(call)

        // TODO: don't compare strings
        if (processed.contains(call.text)) {
            return
        }

        val updatedCall = call.replaceWithNotification(callPrefix)
        tryProcessPartialUsageWithoutArguments(updatedCall, def)

        val refs = searchRefsInPsiElement(def, updatedCall)
        if (refs.isEmpty()) return

        val callerRef = extractRefIdFromCalling(def, updatedCall)
        for (ref in refs) {
            if (ref.element != callerRef) {
                replaceWithSubTerms(ref.element, def, switchedArgIndexInDef)
            }
        }

        // after wrapping in parens it should be always true
        if (def == resolveCaller(updatedCall)) {
            val rewrittenCall = rewriteFunctionCalling(
                usage,
                updatedCall,
                def,
                switchedArgIndexInDef
            )

            if (needUnwrap && rewrittenCall !is ArendLamExpr) {
                val tuple = updatedCall.ancestor<ArendTuple>()!!
                val parentCall = getParentPsiFunctionCall(tuple)
                val child = getTopChildOnPath(tuple, parentCall)

                val (first, last) =
                    if (child is ArendAtomFieldsAcc) {
                        Pair(rewrittenCall.firstChild, rewrittenCall.lastChild)
                    } else {
                        assert(child is ArendAtomArgument)
                        val dummyExpr =
                            factory.createExpression("dummy ${rewrittenCall.text}")
                                .childOfType<ArendArgumentAppExpr>()!!
                        Pair(dummyExpr.children[1], dummyExpr.lastChild)
                    }

                parentCall.addRangeAfterWithNotification(first, last, child)
                child.deleteWithNotification()
            } else {
                val psiToBeReplace =
                    if (rewrittenCall is ArendLamExpr) {
                        updatedCall.ancestor<ArendNewExpr>() ?: updatedCall
                    } else updatedCall

                psiToBeReplace.replaceWithNotification(rewrittenCall)
            }

            processed.add(rewrittenCall.text)
        }
    }

    private fun getParametersIndices(def: PsiElement, call: PsiElement): List<Int> {
        val parameterHandler = ArendParameterInfoHandler()
        val parameters = parameterHandler.getAllParametersForReferable(def as PsiReferable)

        val argsExplicitness = getCallingParametersWithPhantom(call).map { it.first() != '{' }
        val argsIndices = mutableListOf<Int>()

        for (i in argsExplicitness.indices) {
            argsIndices.add(parameterHandler.findParamIndex(parameters, argsExplicitness.subList(0, i + 1)))
        }

        val cntPhantomArgs = argsExplicitness.size - getCallingParameters(call).size
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
        usage: PsiElement,
        call: PsiElement,
        def: PsiElement,
        switchedArgumentIndexInDef: Int
    ): PsiElement {
        val functionName = usage.text.replace("`", "")
        val argsText = getCallingParameters(call).map { it.text } as MutableList<String>
        val indices = getParametersIndices(def, call) as MutableList<Int>
        val lastIndex = indices.maxOrNull() ?: -1
        val needRewriteToLambda = (lastIndex < switchedArgumentIndexInDef)

        if (needRewriteToLambda) {
            /////////////////////////////
            val teleExplicitness = mutableListOf<Boolean>()
            for (tele in getTelesFromDef(def)) {
                for (_param in getTele(tele)!!) {
                    teleExplicitness.add(tele.text.first() != '{')
                }
            }

            val omittedParametersExplicitness = teleExplicitness.subList(lastIndex + 1, teleExplicitness.size)
            val allOmitted = omittedParametersExplicitness.all { !it }

            if (allOmitted) {
                return createPsiFromText("${call.text} _", call)
            }
            /////////////////////////////
            return rewriteToLambda(call, def, switchedArgumentIndexInDef, lastIndex)
        }

        val existsInArgList = (indices.indexOf(switchedArgumentIndexInDef) != -1)
        if (!existsInArgList) {
            val insertAfterIndex = -indices.binarySearch(switchedArgumentIndexInDef) - 1
            argsText.add(insertAfterIndex, "{_}")
            indices.add(insertAfterIndex, switchedArgumentIndexInDef)
        }

        val switchedArgumentIndexInArgs = indices.indexOf(switchedArgumentIndexInDef)
        val expr = buildFunctionCallingText(functionName, argsText, indices, switchedArgumentIndexInDef)

        val newCall = createPsiFromText(expr.trimEnd(), call)
        val newSwitchedArgument = getCallingParameters(newCall)[switchedArgumentIndexInArgs]

        if (!existsInArgList) {
            val anchor = getCallingParameters(call)[switchedArgumentIndexInArgs]
            call.addBeforeWithNotification(newSwitchedArgument, anchor)

            val psiWs = factory.createWhitespace(" ")
            val anchorInsertedArg = getCallingParameters(call)[switchedArgumentIndexInArgs]
            call.addAfterWithNotification(psiWs, anchorInsertedArg)
        } else {
            val isNextArgExplicit =
                if (switchedArgumentIndexInArgs != indices.size - 1) (argsText[switchedArgumentIndexInArgs + 1].first() != '{') else true
            val needToRemoveArg = (argsText[switchedArgumentIndexInArgs] == "_") && isNextArgExplicit
            var oldArg = getCallingParameters(call)[switchedArgumentIndexInArgs]

            if (needToRemoveArg) {
                val prevWs = oldArg.prevSibling
                prevWs?.deleteWithNotification()
                oldArg.deleteWithNotification()
            } else {
                // from explicit to implicit
                // need to add all omitted previous implicit args
                oldArg = oldArg.replaceWithNotification(newSwitchedArgument)

                if (newSwitchedArgument.text.first() == '{') {
                    val paramsBefore =
                        if (switchedArgumentIndexInArgs == 0) indices[switchedArgumentIndexInArgs] else (indices[switchedArgumentIndexInArgs] - indices[switchedArgumentIndexInArgs - 1] - 1)

                    if (paramsBefore > 0) {
                        addArgumentSequenceBefore(factory, "{_} ".repeat(paramsBefore), call, oldArg)
                    }
                }
            }
        }
        return call
    }

    private fun rewriteToLambda(
        call: PsiElement,
        def: PsiElement,
        switchedArgIndexInDef: Int,
        startFromIndex: Int
    ): ArendLamExpr {
        val teleList = mutableListOf<String>()
        val referables = getContext(call) as MutableList

        for (tele in getTelesFromDef(def)) {
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
        callingArgs[switchedArgIndexInDef] = rewriteArg(callingArgs[switchedArgIndexInDef], false)

        val callingArgsCut = callingArgs.subList(startFromIndex + 1, callingArgs.size)
        val newCallText = call.text + callingArgsCut.joinToString(" ", " ")
        return factory.createLam(teleListCut, newCallText)
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
        call: PsiElement,
        def: PsiElement
    ) {
        val parameters = getCallingParameters(call)
        for ((i, parameter) in parameters.withIndex()) {
            if (parameter.text == "_" || parameter.text.contains(" ")) continue
            val paramRef = parameter.childOfType<ArendLongName>() ?: parameter.childOfType<ArendRefIdentifier>()
            ?: continue

            val resolved = if (paramRef is ArendLongName) {
                getRefToFunFromLongName(paramRef)
            } else (paramRef as ArendRefIdentifier).resolve

            if (resolved == def) {
                val argumentInBraces = factory.createArgument("(${parameter.text})")
                val ithArg = getCallingParameters(call)[i]
                ithArg.replaceWithNotification(argumentInBraces)
            }
        }
    }

    abstract fun getParentPsiFunctionCall(element: PsiElement): PsiElement

    abstract fun convertFunctionCallToPrefix(call: PsiElement): PsiElement?

    abstract fun getCallingParameters(call: PsiElement): List<PsiElement>

    abstract fun resolveCaller(call: PsiElement): PsiElement?

    abstract fun createPsiFromText(expr: String, call: PsiElement): PsiElement

    abstract fun getContext(element: PsiElement): List<Variable>

    abstract fun extractRefIdFromCalling(def: PsiElement, call: PsiElement): PsiElement?

    abstract fun getCallingParametersWithPhantom(call: PsiElement): List<String>
}

class SwitchParamImplicitnessNameFieldApplier : SwitchParamImplicitnessApplier() {
    override fun getParentPsiFunctionCall(element: PsiElement): PsiElement =
        element.parent?.ancestor<ArendArgumentAppExpr>() ?: element

    override fun convertFunctionCallToPrefix(call: PsiElement): PsiElement? {
        val concrete = convertCallToConcrete(call) ?: return null
        val functionCallText = buildPrefixTextFromConcrete(concrete)
        return createPsiFromText(functionCallText, call)
    }

    override fun getCallingParameters(call: PsiElement): List<PsiElement> {
        val psiFunctionCall = call as ArendArgumentAppExpr
        return psiFunctionCall.argumentList
    }

    override fun createPsiFromText(expr: String, call: PsiElement): PsiElement {
        return factory.createExpression(expr).childOfType<ArendArgumentAppExpr>()!!
    }

    override fun getContext(element: PsiElement): List<Variable> {
        val argumentAppExpr = element as ArendArgumentAppExpr
        return argumentAppExpr.scope.elements.map { VariableImpl(it.textRepresentation()) }
    }

    override fun extractRefIdFromCalling(def: PsiElement, call: PsiElement): PsiElement? {
        val function = (call as ArendArgumentAppExpr).atomFieldsAcc!!
        val refs = searchRefsInPsiElement(def, function)
        return if (refs.isEmpty()) null else refs.first().element
    }

    override fun getCallingParametersWithPhantom(call: PsiElement): List<String> {
        val concrete = convertCallToConcrete(call)
        return concrete?.arguments?.map { if (it.isExplicit) it.toString() else "{$it}" } ?: emptyList()
    }

    override fun resolveCaller(call: PsiElement): PsiElement? {
        val psiFunctionCall = call as ArendArgumentAppExpr
        val longName = psiFunctionCall.atomFieldsAcc?.childOfType<ArendLongName>()
        longName ?: return null
        return getRefToFunFromLongName(longName)
    }
}

class SwitchParamImplicitnessTypeApplier : SwitchParamImplicitnessApplier() {
    override fun getParentPsiFunctionCall(element: PsiElement): PsiElement {
        return element.parent?.ancestor<ArendLocalCoClause>() ?: element
    }

    override fun convertFunctionCallToPrefix(call: PsiElement): PsiElement = call

    override fun getCallingParameters(call: PsiElement): List<PsiElement> {
        val psiFunctionCall = call as ArendLocalCoClause
        return psiFunctionCall.lamParamList
    }

    override fun createPsiFromText(expr: String, call: PsiElement): PsiElement {
        val body = (call as ArendLocalCoClause).expr?.text
        return factory.createLocalCoClause(expr, body)
    }

    override fun getContext(element: PsiElement): List<Variable> {
        val localCoClause = element as ArendLocalCoClause
        return localCoClause.scope.elements.map { VariableImpl(it.textRepresentation()) }
    }

    override fun extractRefIdFromCalling(def: PsiElement, call: PsiElement): PsiElement? {
        val function = (call as ArendLocalCoClause).longName!!
        val refs = searchRefsInPsiElement(def, function)
        return if (refs.isEmpty()) null else refs.first().element
    }

    override fun getCallingParametersWithPhantom(call: PsiElement): List<String> {
        return getCallingParameters(call).map { it.text }
    }

    override fun resolveCaller(call: PsiElement): PsiElement? {
        val coClause = call as ArendLocalCoClause
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
    call: PsiElement,
    anchor: PsiElement
) {
    val exprPsi = factory.createExpression("dummy $argSequence").childOfType<ArendArgumentAppExpr>()!!
    val (first, last) = Pair(exprPsi.argumentList.first(), exprPsi.argumentList.last())

    val psiWs = factory.createWhitespace(" ")
    call.addRangeBeforeWithNotification(first, last, anchor)
    call.addBeforeWithNotification(psiWs, anchor)
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

private fun tryResolveFunctionName(element: PsiElement): PsiElement? =
    if (element is ArendLongName) {
        getRefToFunFromLongName(element)
    } else {
        element.reference?.resolve()
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

private fun convertCallToConcrete(call: PsiElement): Concrete.AppExpression? {
    val scope = (call as ArendArgumentAppExpr).scope

    return ConcreteBuilder.convertExpression(call as Abstract.Expression)
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

private fun buildPrefixTextFromConcrete(concrete: Concrete.AppExpression): String =
    buildString {
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

private fun getTopChildOnPath(element: PsiElement, parent: PsiElement): PsiElement {
    var current = element
    while (current.parent != null && current.parent != parent) {
        current = current.parent
    }
    return current
}
