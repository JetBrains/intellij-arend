package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.arend.codeInsight.ArendParameterInfoHandler
import org.arend.error.DummyErrorReporter
import org.arend.ext.concrete.expr.ConcreteExpression
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

class ChangeArgumentExplicitnessIntention : SelfTargetingIntention<ArendCompositeElement>(
    ArendCompositeElement::class.java,
    ArendBundle.message("arend.coClause.changeArgumentExplicitness")
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

    private fun chooseApplier(element: ArendCompositeElement): ChangeArgumentExplicitnessApplier? {
        return when (element) {
            is ArendNameTele, is ArendFieldTele -> NameFieldApplier()
            is ArendTypeTele -> TypeApplier()
            else -> null
        }
    }

    private fun getSwitchedArgIndex(tele: ArendCompositeElement, switchedArg: PsiElement?): Int? {
        switchedArg ?: return -1
        val argsText = getTele(tele)?.map { it.text }
        return if (argsText?.size == 1) 0 else argsText?.indexOf(switchedArg.text)
    }

    private fun switchTeleExplicitness(tele: ArendCompositeElement) {
        val def = tele.ancestor<PsiReferable>() as PsiElement
        val teleIndex = def.children.indexOf(tele)
        val anchor = def.children[teleIndex - 1]
        val factory = ArendPsiFactory(tele.project)
        val newTele = createSwitchedTele(factory, tele) ?: return

        var curElement = tele
        val teleSize = getTele(tele)?.size ?: return
        for (i in 0..teleSize) {
            val splittedTele = chooseApplier(curElement)?.applyTo(curElement, 0) ?: continue
            curElement = splittedTele.nextSibling as? ArendCompositeElement ?: break
        }

        val (first, last) = Pair(def.children[teleIndex], def.children[teleIndex + teleSize - 1])
        def.deleteChildRangeWithNotification(first, last)
        val inserted = def.addAfterWithNotification(newTele, anchor)
        val ws = factory.createWhitespace(" ")
        def.addBeforeWithNotification(ws, inserted)
    }
}

abstract class ChangeArgumentExplicitnessApplier {
    protected lateinit var factory: ArendPsiFactory
    private val processed = mutableSetOf<String>()
    private val wrapped = mutableSetOf<PsiElement>()

    fun applyTo(element: ArendCompositeElement, indexInTele: Int): PsiElement {
        factory = ArendPsiFactory(element.project)

        val def = element.ancestor<PsiReferable>() as? PsiElement ?: return element
        val indexInDef = getTeleIndexInDef(def, element) + indexInTele

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
            if (!inOpenDeclaration(ref.element)) {
                replaceWithSubTerms(ref.element, def, indexInDef)
            }
        }

        return rewriteDef(element, indexInTele)
    }

    /*
        Before rewriting calls, it's helpful to wrap into parens each call.
        It makes all rewriting locally, so it's not necessary to go up to the topmost call.
        After rewriting the parens will be removed.
    */
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
            return wrapCall(concrete)
        }

        return null
    }

    /*
        Initially, this function replaces all children of given element `usage`, which contains references to `def`.
        Then it converts the current to the prefix form (optional) and rewrites this.
    */
    private fun replaceWithSubTerms(usage: PsiElement, def: PsiElement, indexInDef: Int) {
        // this element has already been replaced
        if (!usage.isValid) return

        val call = getParentPsiFunctionCall(usage)
        if (processed.contains(call.text)) return

        val callPrefix = convertFunctionCallToPrefix(call) ?: call
        val needUnwrap = wrapped.contains(call)

        val updatedCall = call.replaceWithNotification(callPrefix)
        tryProcessPartialUsageWithoutArguments(updatedCall, def)

        val refs = searchRefsInPsiElement(def, updatedCall)
        if (refs.isEmpty()) return

        val callerRef = extractRefIdFromCalling(def, updatedCall)
        for (ref in refs) {
            if (ref.element != callerRef) {
                replaceWithSubTerms(ref.element, def, indexInDef)
            }
        }

        // after wrapping in parens it should be true for most cases
        if (def == resolveCaller(updatedCall)) {
            val rewrittenCall = rewriteCall(updatedCall, def, indexInDef)
            if (needUnwrap && rewrittenCall !is ArendLamExpr) {
                unwrapCall(rewrittenCall)
            } else {
                val psiWillBeReplaced = if (rewrittenCall is ArendLamExpr) updatedCall.ancestor<ArendNewExpr>()
                    ?: updatedCall else updatedCall
                psiWillBeReplaced.replaceWithNotification(rewrittenCall)
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

    private fun rewriteDef(tele: ArendCompositeElement, indexInTele: Int): PsiElement {
        val teleSize = getTele(tele)?.size
        if (teleSize != null && teleSize > 1) {
            splitTele(tele, indexInTele)
        }

        val newTele = createSwitchedTele(factory, tele)
        newTele ?: return tele

        return tele.replaceWithNotification(newTele)
    }

    private fun rewriteCall(call: PsiElement, def: PsiElement, indexInDef: Int): PsiElement {
        val argsText = getCallingParameters(call).map { it.text } as MutableList<String>
        val indices = getParametersIndices(def, call) as MutableList<Int>
        val lastIndex = indices.maxOrNull() ?: -1
        val notEnoughArguments = (lastIndex < indexInDef)

        if (notEnoughArguments) {
            if (lastImplicitArgumentsAreOmitted(def, lastIndex)) {
                insertArgumentInIndex(call, createArgument("_"), argsText.size)
                return call
            }
            return rewriteToLambda(call, def, indexInDef, lastIndex)
        }

        val existsInArgList = (indices.indexOf(indexInDef) != -1)
        if (!existsInArgList) {
            val insertInIndex = -indices.binarySearch(indexInDef) - 1
            val underscoreArg = createArgument("_")
            insertArgumentInIndex(call, underscoreArg, insertInIndex)
            return call
        }

        val indexInArgs = indices.indexOf(indexInDef)
        val isNextExplicit = if (indexInArgs != indices.size - 1) (argsText[indexInArgs + 1].first() != '{') else true
        val needToRemoveArg = (argsText[indexInArgs] == "_") && isNextExplicit

        if (needToRemoveArg) {
            deleteArgumentInIndex(call, indexInArgs)
        } else {
            val newArgText = rewriteArg(argsText[indexInArgs])
            val newArgument = createArgument(newArgText)
            changeArgumentInIndex(call, newArgument, def, indexInArgs)
        }
        return call
    }

    private fun lastImplicitArgumentsAreOmitted(def: PsiElement, lastIndexInCall: Int): Boolean {
        val teleExplicitness = mutableListOf<Boolean>()
        for (tele in getTelesFromDef(def)) {
            val teleSize = getTele(tele)?.size ?: continue
            teleExplicitness.addAll(List(teleSize) { tele.text.first() != '{' })
        }

        val omittedParametersExplicitness = teleExplicitness.subList(lastIndexInCall + 1, teleExplicitness.size)
        return omittedParametersExplicitness.all { !it }
    }

    private fun rewriteToLambda(call: PsiElement, def: PsiElement, indexInDef: Int, startFromIndex: Int): ArendLamExpr {
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
        callingArgs[indexInDef] = rewriteArg(callingArgs[indexInDef])

        val callingArgsCut = callingArgs.subList(startFromIndex + 1, callingArgs.size)
        val newCallText = call.text + callingArgsCut.joinToString(" ", " ")
        return factory.createLam(teleListCut, newCallText)
    }

    /*
       wrap the function, when its in arguments, in parens.
       case:
       \func foo (f : (Nat -> Nat) -> Nat) => f suc
       converts to:
       \func foo (f : (Nat -> Nat) -> Nat) => f (suc)
    */
    private fun tryProcessPartialUsageWithoutArguments(call: PsiElement, def: PsiElement) {
        val parameters = getCallingParameters(call)
        for ((i, parameter) in parameters.withIndex()) {
            if (parameter.text == "_" || parameter.text.contains(" ")) continue
            val paramRef = parameter.childOfType<ArendLongName>() ?: parameter.childOfType<ArendRefIdentifier>()
            ?: continue

            val resolved = if (paramRef is ArendLongName) {
                getRefToFunFromLongName(paramRef)
            } else (paramRef as ArendRefIdentifier).resolve

            if (resolved == def) {
                val argumentInBraces = createArgument("(${parameter.text})")
                val ithArg = getCallingParameters(call)[i]
                ithArg.replaceWithNotification(argumentInBraces)
            }
        }
    }

    private fun insertArgumentInIndex(call: PsiElement, argument: PsiElement, index: Int) {
        val parameters = getCallingParameters(call)
        val ws = factory.createWhitespace(" ")

        if (index == parameters.size) {
            val anchor = call.addAfterWithNotification(ws, call.lastChild)
            call.addAfterWithNotification(argument, anchor)
            return
        }
        val previousArgument = parameters[index]
        val anchor = call.addBeforeWithNotification(argument, previousArgument)
        call.addAfterWithNotification(ws, anchor)
    }

    private fun changeArgumentInIndex(call: PsiElement, argument: PsiElement, def: PsiElement, index: Int) {
        val indices = getParametersIndices(def, call)
        val oldArgument = getCallingParameters(call)[index]
        val anchor = oldArgument.replaceWithNotification(argument)

        // if new argument is implicit, then the previous implicit arguments must be inserted
        val isExplicit = argument.text.first() != '{'
        if (!isExplicit) {
            val parametersBefore = if (index == 0) indices[index] else (indices[index] - indices[index - 1] - 1)
            if (parametersBefore > 0) {
                addArgumentSequenceBefore(factory, "{_} ".repeat(parametersBefore), call, anchor)
            }
        }
    }

    private fun deleteArgumentInIndex(call: PsiElement, index: Int) {
        val argument = getCallingParameters(call)[index]
        val previousWs = argument.prevSibling
        previousWs?.deleteWithNotification()
        argument.deleteWithNotification()
    }

    private fun wrapCall(concrete: Concrete.AppExpression): PsiElement? {
        val call = getParentPsiFunctionCall(concrete.function.data as PsiElement)
        val (first, last) = getRangeForConcrete(concrete, call) ?: return null

        val isAlreadyWrapped =
            wrapped.contains(call) ||
                    !call.isValid ||
                    ("(${call.text})" == call.ancestor<ArendTuple>()?.text &&
                            call.firstChild == first &&
                            call.lastChild == last)

        if (isAlreadyWrapped) return null

        val callText = buildPrefixTextFromConcrete(concrete)
        val newCall = buildString {
            for (child in call.children) {
                when (child) {
                    first -> {
                        append("($callText) ")
                        break
                    }
                    else -> {
                        append("${child.text} ")
                    }
                }
            }
        }.trimEnd()

        val wrappedCall = factory.createArgumentAppExpr(newCall).children.last()
        val insertedCall = call.addAfterWithNotification(wrappedCall, last)
        call.deleteChildRangeWithNotification(first, last)
        wrapped.add(insertedCall.childOfType<ArendArgumentAppExpr>()!!)

        return call
    }

    private fun unwrapCall(call: PsiElement) {
        fun callToRange(call: PsiElement, firstChild: PsiElement): Pair<PsiElement, PsiElement> =
            if (firstChild is ArendAtomFieldsAcc) {
                Pair(call.firstChild, call.lastChild)
            } else {
                val dummyExpr = factory.createArgumentAppExpr("dummy ${call.text}")
                Pair(dummyExpr.children[1], dummyExpr.lastChild)
            }

        val tuple = call.ancestor<ArendTuple>() ?: return
        val parentCall = getParentPsiFunctionCall(tuple)
        val child = getTopChildOnPath(tuple, parentCall)
        val (first, last) = callToRange(call, child)
        parentCall.addRangeAfterWithNotification(first, last, child)
        child.deleteWithNotification()
    }

    protected fun buildPrefixTextFromConcrete(concrete: Concrete.AppExpression): String =
        buildString {
            val psiFunction = concrete.function.data as PsiElement
            val call = getParentPsiFunctionCall(psiFunction)
            val functionText = psiFunction.text.replace("`", "")
            append("$functionText ")

            for (arg in concrete.arguments) {
                val concreteArg = arg.expression
                val argText =
                    if (concreteArg !is Concrete.AppExpression) {
                        (concreteArg.data as PsiElement).text
                    } else {
                        val (first, last) = getRangeForConcrete(concreteArg, call) ?: continue
                        getTextForRange(first, last)
                    }

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

    abstract fun getParentPsiFunctionCall(element: PsiElement): PsiElement

    abstract fun convertFunctionCallToPrefix(call: PsiElement): PsiElement?

    abstract fun getCallingParameters(call: PsiElement): List<PsiElement>

    abstract fun resolveCaller(call: PsiElement): PsiElement?

    abstract fun getContext(element: PsiElement): List<Variable>

    abstract fun extractRefIdFromCalling(def: PsiElement, call: PsiElement): PsiElement?

    abstract fun getCallingParametersWithPhantom(call: PsiElement): List<String>

    abstract fun createArgument(arg: String): PsiElement
}

class NameFieldApplier : ChangeArgumentExplicitnessApplier() {
    override fun getParentPsiFunctionCall(element: PsiElement): PsiElement =
        element.parent?.ancestor<ArendArgumentAppExpr>() ?: element

    override fun convertFunctionCallToPrefix(call: PsiElement): PsiElement? {
        val concrete = convertCallToConcrete(call) ?: return null
        val functionCallText = buildPrefixTextFromConcrete(concrete)
        return factory.createArgumentAppExpr(functionCallText)
    }

    override fun getCallingParameters(call: PsiElement): List<PsiElement> = (call as ArendArgumentAppExpr).argumentList

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
        val longName = psiFunctionCall.atomFieldsAcc?.childOfType<ArendLongName>() ?: return null
        return getRefToFunFromLongName(longName)
    }

    override fun createArgument(arg: String): PsiElement =
        factory.createArgumentAppExpr("dummy $arg").argumentList.firstOrNull() ?: error("Failed to create argument ")
}

class TypeApplier : ChangeArgumentExplicitnessApplier() {
    override fun getParentPsiFunctionCall(element: PsiElement): PsiElement {
        return element.parent?.ancestor<ArendLocalCoClause>() ?: element
    }

    override fun convertFunctionCallToPrefix(call: PsiElement): PsiElement = call

    override fun getCallingParameters(call: PsiElement): List<PsiElement> = (call as ArendLocalCoClause).lamParamList

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
        val longName = coClause.longName ?: return null
        return getRefToFunFromLongName(longName)
    }

    override fun createArgument(arg: String): PsiElement = factory.createCoClause("dummy $arg").lamParamList.first()
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
            factory.createFieldTele(params, type, !isExplicit)
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

private fun rewriteArg(text: String): String {
    var newText: String
    val toExplicit = (text.first() == '{')

    if (toExplicit) {
        newText = text.substring(1, text.length - 1)
        if (needToWrapInBrackets(text)) {
            newText = "($newText)"
        }
        return newText
    }

    return "{$text}"
}

private fun getTeleIndexInDef(def: PsiElement, tele: PsiElement): Int {
    val parameterHandler = ArendParameterInfoHandler()
    val parameters = parameterHandler.getAllParametersForReferable(def as PsiReferable)

    var i = 0
    for (parameter in parameters) {
        if (parameter == tele) return i
        val teles = getTele(parameter as PsiElement) ?: continue
        i += teles.size
    }
    return -1
}

private fun addArgumentSequenceBefore(factory: ArendPsiFactory, argSeq: String, call: PsiElement, anchor: PsiElement) {
    val exprPsi = factory.createExpression("dummy $argSeq").childOfType<ArendArgumentAppExpr>()!!
    val (first, last) = Pair(exprPsi.argumentList.first(), exprPsi.argumentList.last())

    val psiWs = factory.createWhitespace(" ")
    call.addRangeBeforeWithNotification(first, last, anchor)
    call.addBeforeWithNotification(psiWs, anchor)
}

private fun ArendPsiFactory.createArgumentAppExpr(expr: String): ArendArgumentAppExpr =
    createExpression(expr).childOfType() ?: error("Failed to create argument app expr: `$expr`")

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
    val scope = (call as? ArendArgumentAppExpr)?.scope ?: return null

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

private fun getTopChildOnPath(element: PsiElement, parent: PsiElement): PsiElement {
    var current = element
    while (current.parent != null && current.parent != parent) {
        current = current.parent
    }
    return current
}

private fun getRangeForConcrete(concrete: Concrete.Expression, topCall: PsiElement): Pair<PsiElement, PsiElement>? {
    fun getTopExprByComparator(
        argumentsSequence: List<ConcreteExpression>,
        comp: (List<ConcreteExpression>) -> ConcreteExpression?
    ): ConcreteExpression? {
        val argOrAppExpr = comp(argumentsSequence) ?: return null
        return if (argOrAppExpr is Concrete.AppExpression) comp(argOrAppExpr.argumentsSequence.map { it.expression })
        else argOrAppExpr
    }

    val argumentsSequence = concrete.argumentsSequence.map { it.expression }
    val first =
        getTopExprByComparator(argumentsSequence) { seq -> seq.minByOrNull { (it.data as PsiElement).textOffset } }
            ?: return null
    val last =
        getTopExprByComparator(argumentsSequence) { seq -> seq.maxByOrNull { (it.data as PsiElement).textOffset } }
            ?: return null

    return Pair(
        getTopChildOnPath(first.data as PsiElement, topCall),
        getTopChildOnPath(last.data as PsiElement, topCall)
    )
}

private fun getTextForRange(first: PsiElement, last: PsiElement) = buildString {
    var current = first
    append(current.text)
    while (current != last) {
        current = current.nextSibling
        append(current.text)
    }
}

private fun inOpenDeclaration(usage: PsiElement): Boolean = usage.ancestor<ArendStatCmd>()?.openKw != null
