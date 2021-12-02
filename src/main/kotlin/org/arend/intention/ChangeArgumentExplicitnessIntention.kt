package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.arend.codeInsight.ArendParameterInfoHandler.Companion.findParamIndex
import org.arend.codeInsight.ArendParameterInfoHandler.Companion.getAllParametersForReferable
import org.arend.error.DummyErrorReporter
import org.arend.ext.concrete.expr.ConcreteExpression
import org.arend.ext.variable.Variable
import org.arend.ext.variable.VariableImpl
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.renamer.StringRenamer
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.Scope
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

/*
    The key steps of this intention:
      * Wraps into parens all calls which contains function with changed argument. It helps not to go up very high in the term.
        Firstly, we try to wrap the most inner calls and then the top

      * Rewrite all usages. Again, we try to rewrite the most inner call and then the top.
        It saves from the inner psi-element becoming invalid.

      * Rewrite definition

      * If many arguments were switched, the steps are repeated
 */

class ChangeArgumentExplicitnessIntention : SelfTargetingIntention<ArendCompositeElement>(
    ArendCompositeElement::class.java,
    ArendBundle.message("arend.coClause.changeArgumentExplicitness")
) {
    override fun isApplicableTo(element: ArendCompositeElement, caretOffset: Int, editor: Editor): Boolean {
        return when (element) {
            is ArendNameTele, is ArendFieldTele, is ArendTypeTele ->
                element.parent?.let{ it is ArendDefinition || it is ArendClassField || it is ArendConstructor } ?: false
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
            is ArendTypeTele -> if (element.parent is ArendConstructor || element.parent is ArendDefData) NameFieldApplier() else TypeApplier()
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
        val teleSize = if (tele is ArendTypeTele && tele.typedExpr?.identifierOrUnknownList?.isEmpty() == true) 1 else getTele(tele)?.size ?: return
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

    /**
     * Rewrites all usages and definition
     *
     * @param element  argument which explicitness was changed
     * @param indexInTele  argument index among all arguments
     * @return  new telescope in definition
     */
    fun applyTo(element: ArendCompositeElement, indexInTele: Int): PsiElement {
        factory = ArendPsiFactory(element.project)
        val def = element.ancestor<PsiReferable>() as? PsiElement ?: return element
        val indexInDef = getTeleIndexInDef(def, element) + indexInTele
        val fCalls = ReferencesSearch.search(def).map { it.element }.filter { it.isValid }
        val insertedPsi = ArrayList<PsiElement>()
        val scopes = HashMap<PsiElement, Scope>()
        if (element !is ArendTypeTele) {
            for (fCall in fCalls.map { getParentPsiFunctionCall(it) }.sortedBy { it.textLength }) {
                val scopeAnchor = (fCall.ancestor<ArendDefinition>()?: fCall) as? ArendCompositeElement
                val scope = if (scopeAnchor != null) scopes.computeIfAbsent(scopeAnchor) { CachingScope.make(scopeAnchor.scope) } else null
                if (scope != null) CallWrapper.wrapWithSubTerms(fCall, def, insertedPsi, scope)
            }
        }

        val updatedCalls = ArrayList(fCalls)
        insertedPsi.filter { it.isValid }.map { call -> updatedCalls.addAll(searchRefsInPsiElement(def, call).map { it.element }) }

        for (ref in updatedCalls.filter{ it.isValid && !inOpenDeclaration(it) }) replaceWithSubTerms(ref, def, indexInDef)

        return rewriteDef(element, indexInTele)
    }

    /**
     * Initially, this function rewrites all calls of `def`-function inside call of `usage`.
     * Then it converts the current to the prefix form (if needed) and rewrites this.
     *
     * @param usage  usage which call will be rewritten
     * @param def  rewrite call only if `resolve(usage) == def`
     * @param indexInDef  index of changed argument in definition
     */
    private fun replaceWithSubTerms(usage: PsiElement, def: PsiElement, indexInDef: Int) {
        // this element has already been replaced
        if (!usage.isValid) return

        val call = getParentPsiFunctionCall(usage)
        if (processed.contains(call.text)) return

        val callPrefix = convertCallToPrefix(call) ?: call
        val needUnwrap = CallWrapper.needUnwrap(call)

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

        // after wrapping in parens it should be true for most cases, because we shouldn't go up a lot
        if (def == resolveCaller(updatedCall)) {
            val rewrittenCall = rewriteCall(updatedCall, def, indexInDef)
            if (needUnwrap && rewrittenCall !is ArendLamExpr) {
                CallWrapper.unwrapCall(rewrittenCall)
            } else {
                val psiWillBeReplaced = if (rewrittenCall is ArendLamExpr) updatedCall.ancestor<ArendNewExpr>()
                    ?: updatedCall else updatedCall
                psiWillBeReplaced.replaceWithNotification(rewrittenCall)
            }

            processed.add(rewrittenCall.text)
        }
    }

    /**
     * Maps argument index in call to its index in definition (takes phantom args into account)
     *
     * @return  list, where list.i = j means that ith argument in call is jth argument from definition
     */
    private fun getParametersIndices(def: PsiElement, call: PsiElement): List<Int> {
        val parameters = getAllParametersForReferable(def as PsiReferable)
        val argsExplicitness = getCallingParametersWithPhantom(call).map { it.first() != '{' }
        val argsIndices = mutableListOf<Int>()
        for (i in argsExplicitness.indices) {
            argsIndices.add(findParamIndex(parameters, argsExplicitness.subList(0, i + 1)))
        }

        val cntPhantomArgs = argsExplicitness.size - getCallingParameters(call).size
        return argsIndices.subList(cntPhantomArgs, argsIndices.size)
    }

    /**
     * @param tele  telescope which explicitness was changed
     * @param indexInTele  argument index among all arguments
     * @return  new telescope in definition
     */
    private fun rewriteDef(tele: ArendCompositeElement, indexInTele: Int): PsiElement {
        val teleSize = getTele(tele)?.size
        if (teleSize != null && teleSize > 1) {
            splitTele(tele, indexInTele)
        }

        val newTele = createSwitchedTele(factory, tele)
        newTele ?: return tele

        return tele.replaceWithNotification(newTele)
    }

    /**
     * Rewrites argument with index `indexInDef` in call
     *
     * @param indexInDef  changed argument index in function definition
     * @return  psi with rewritten call
     */
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

    /**
     * It's dummy function. Indicates whether last implicit arguments were passed to the function or not.
     *
     * @param lastIndexInCall  last index of argument which was passed in the call
     * @return  last implicit arguments were passed to the function or not
     */
    private fun lastImplicitArgumentsAreOmitted(def: PsiElement, lastIndexInCall: Int): Boolean {
        val teleExplicitness = getTelesFromDef(def)?.map { it.second } ?: throw IllegalStateException()
        val omittedParametersExplicitness = teleExplicitness.subList(lastIndexInCall + 1, teleExplicitness.size)
        return omittedParametersExplicitness.all { !it }
    }

    /**
     * Rewrites partial application to lambda expression
     *
     * @param indexInDef  changed argument index in definition
     * @param startFromIndex  first argument index which doesn't exists in the call
     * @return  psi with lambda
     */
    private fun rewriteToLambda(call: PsiElement, def: PsiElement, indexInDef: Int, startFromIndex: Int): ArendLamExpr {
        val referables = getContext(call) as MutableList
        val teleList = getTelesFromDef(def)?.map {
            val variable = VariableImpl(it.first)
            val freshName = StringRenamer().generateFreshName(variable, referables)
            referables.add(VariableImpl(freshName))
            if (it.second) freshName else "{$freshName}"
        } ?: throw IllegalStateException()

        val teleListCut = teleList.subList(startFromIndex + 1, teleList.size)
        val callingArgs = teleList.toMutableList()
        callingArgs[indexInDef] = rewriteArg(callingArgs[indexInDef])

        val callingArgsCut = callingArgs.subList(startFromIndex + 1, callingArgs.size)
        val newCallText = call.text + callingArgsCut.joinToString(" ", " ")
        return factory.createLam(teleListCut, newCallText)
    }

    /**
       Wraps the function, when its in arguments, in parens.
       Example:
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

    /**
     * Inserts argument into call at index in the call
     */
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

    /**
     * Changes argument's explicitness in the call
     */
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

    /**
     * Deletes argument at index in the call
     */
    private fun deleteArgumentInIndex(call: PsiElement, index: Int) {
        val argument = getCallingParameters(call)[index]
        val previousWs = argument.prevSibling
        previousWs?.deleteWithNotification()
        argument.deleteWithNotification()
    }

    /**
     * Returns the closest ancestor psi-call for element
     */
    abstract fun getParentPsiFunctionCall(element: PsiElement): PsiElement

    abstract fun convertCallToPrefix(call: PsiElement): PsiElement?

    /**
     * @return  parameters for given call
     */
    abstract fun getCallingParameters(call: PsiElement): List<PsiElement>

    /**
     * Resolves the function of given call
     */
    abstract fun resolveCaller(call: PsiElement): PsiElement?

    abstract fun getContext(element: PsiElement): List<Variable>

    /**
     * Extracts reference to the function of given call
     */
    abstract fun extractRefIdFromCalling(def: PsiElement, call: PsiElement): PsiElement?

    /**
     * Returns all parameters including phantoms
     */
    abstract fun getCallingParametersWithPhantom(call: PsiElement): List<String>

    /**
     * Creates psi (AtomArgument) from text
     */
    abstract fun createArgument(arg: String): PsiElement
}

class NameFieldApplier : ChangeArgumentExplicitnessApplier() {
    override fun getParentPsiFunctionCall(element: PsiElement): PsiElement =
        element.ancestor<ArendArgumentAppExpr>() ?: element

    override fun convertCallToPrefix(call: PsiElement): PsiElement? {
        val scope = CachingScope.make((((call.ancestor<ArendDefinition>() ?: call) as? ArendCompositeElement) ?: return null).scope)
        val concrete = convertCallToConcrete(call, scope) ?: return null
        val functionCallText = textOfConcreteAppExpression(concrete)
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
        val scope = CachingScope.make(((call.ancestor<ArendDefinition>() ?: call) as? ArendCompositeElement)?.scope)
        val concrete = convertCallToConcrete(call, scope)
        return concrete?.arguments?.map { if (it.isExplicit) it.toString() else "{$it}" } ?: emptyList()
    }

    override fun resolveCaller(call: PsiElement): PsiElement? {
        val psiFunctionCall = call as ArendArgumentAppExpr
        val longName = psiFunctionCall.atomFieldsAcc?.childOfType<ArendLongName>() ?: return null
        return getRefToFunFromLongName(longName)
    }

    override fun createArgument(arg: String): PsiElement =
        factory.createArgumentAppExpr("dummy $arg").argumentList.firstOrNull() ?:
        error("Failed to create argument ")
}

class TypeApplier : ChangeArgumentExplicitnessApplier() {
    override fun getParentPsiFunctionCall(element: PsiElement): PsiElement =
        element.ancestor<ArendLocalCoClause>() ?: element

    override fun convertCallToPrefix(call: PsiElement): PsiElement = call

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

private object CallWrapper {
    private val wrapped = mutableSetOf<PsiElement>()
    private lateinit var factory: ArendPsiFactory

    /**
        Wraps into parens all calls in `call`-element where resolve of function equals `def`
    */
    fun wrapWithSubTerms(call: PsiElement, def: PsiElement, insertedPsi: MutableList<PsiElement>, scope: Scope) {
        fun wrapInArguments(appExpr: Concrete.AppExpression, def: PsiElement, index: Int, needResult: Boolean): Concrete.AppExpression? {
            var concrete = appExpr
            val cntArguments = concrete.arguments.size
            val function = concrete.function.data as PsiElement

            // It's important to update concrete, because its `data` becomes dummy after rewriting
            for (i in 0 until cntArguments) {
                val argument = concrete.arguments[i].expression
                if (argument is Concrete.AppExpression) {
                    val currentCall = wrapInArguments(argument, def, i, true)!!
                    // it's hard to find after wrapping psi-call corresponding argument
                    concrete = if (currentCall.toString() != appExpr.toString()) {
                        val newCall = currentCall.arguments.getOrNull(index)?.expression as? Concrete.AppExpression
                        if (newCall == null || newCall.toString() != appExpr.toString()) continue
                        newCall
                    } else {
                        currentCall
                    }
                }
            }
            val resolve = tryResolveFunctionName(function)
            return if (def == resolve) wrapCall(concrete, insertedPsi, scope, needResult) else concrete
        }

        factory = ArendPsiFactory(def.project)
        val concrete = convertCallToConcrete(call, scope) ?: return
        wrapInArguments(concrete, def, -1, false)
    }

    /**
     * Wraps the psi-call corresponding `concrete.data`
     *
     * @return  concrete, where call is wrapped
     */
    fun wrapCall(concrete: Concrete.AppExpression, insertedPsi: MutableList<PsiElement>, scope: Scope, needResult: Boolean): Concrete.AppExpression? {
        val call = (concrete.function.data as PsiElement).ancestor<ArendArgumentAppExpr>() ?: return concrete
        val (first, last) = getRangeForConcrete(concrete, call) ?: return concrete
        val isCurrentCallOnTop = (call.firstChild == first && call.lastChild == last)
        val isAlreadyWrapped =
            wrapped.contains(call) ||
                    !call.isValid ||
                    ("(${call.text})" == call.ancestor<ArendTuple>()?.text && isCurrentCallOnTop)

        if (isAlreadyWrapped) return concrete

        val callText = textOfConcreteAppExpression(concrete)
        val newCall = buildString {
            for (child in call.children) {
                if (child == first) {
                    append("($callText) ")
                    break
                } else {
                    append("${child.text} ")
                }
            }
        }.trimEnd()

        val wrappedCall = factory.createArgumentAppExpr(newCall).lastChild
        val insertedCall = call.addAfterWithNotification(wrappedCall, last)
        call.deleteChildRangeWithNotification(first, last)
        wrapped.add(insertedCall.childOfType<ArendArgumentAppExpr>()!!)
        insertedPsi.add(insertedCall)

        return if (needResult) convertCallToConcrete(insertedCall.ancestor<ArendArgumentAppExpr>() ?: insertedCall, scope) ?: concrete else null
    }

    fun unwrapCall(call: PsiElement) {
        fun convertCallToRange(call: PsiElement, firstChild: PsiElement): Pair<PsiElement, PsiElement> =
            if (firstChild is ArendAtomFieldsAcc) {
                Pair(call.firstChild, call.lastChild)
            } else {
                val dummyExpr = factory.createArgumentAppExpr("dummy ${call.text}")
                Pair(dummyExpr.children[1], dummyExpr.lastChild)
            }

        val tuple = call.ancestor<ArendTuple>() ?: return
        val parentCall = tuple.ancestor<ArendArgumentAppExpr>() ?: tuple
        val child = getTopChildOnPath(tuple, parentCall)
        val (first, last) = convertCallToRange(call, child)
        parentCall.addRangeAfterWithNotification(first, last, child)
        child.deleteWithNotification()
    }

    fun needUnwrap(call: PsiElement): Boolean = wrapped.contains(call)
}

/**
 * Creates telescope with a changed parens
 */
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
    val toExplicit = (text.first() == '{' && text.last() == '}')
    val toImplicit = (text.first() == '(' && text.last() == ')')

    if (toExplicit) {
        newText = text.substring(1, text.length - 1)
        if (needToWrapInBrackets(text)) {
            newText = "($newText)"
        }
        return newText
    }

    return if (!toImplicit) {
        if (text.isNotEmpty() && text.first() == '-') "{ $text}" else "{$text}"
    } else {
        newText = text.substring(1, text.length - 1)
        if (newText.isNotEmpty() && newText.first() == '-') "{ $newText}" else "{$newText}"
    }
}

/**
 * Returns first argument's index in this `tele` among all arguments in `def`
 */
private fun getTeleIndexInDef(def: PsiElement, tele: PsiElement): Int {
    val parameters = getAllParametersForReferable(def as PsiReferable)

    var i = 0
    for (parameter in parameters) {
        if (parameter == tele) return i
        i += parameter.referableList.size
    }
    return -1
}

/**
 * Inserts many arguments before `anchor` in `call`
 */
private fun addArgumentSequenceBefore(factory: ArendPsiFactory, argSeq: String, call: PsiElement, anchor: PsiElement) {
    val exprPsi = factory.createExpression("dummy $argSeq").childOfType<ArendArgumentAppExpr>()!!
    val (first, last) = Pair(exprPsi.argumentList.first(), exprPsi.argumentList.last())

    val psiWs = factory.createWhitespace(" ")
    call.addRangeBeforeWithNotification(first, last, anchor)
    call.addBeforeWithNotification(psiWs, anchor)
}

private fun ArendPsiFactory.createArgumentAppExpr(expr: String): ArendArgumentAppExpr =
    createExpression(expr).childOfType() ?: error("Failed to create argument app expr: `$expr`")

/**
 * Returns all references of `def` in `element`-scope
 */
private fun searchRefsInPsiElement(def: PsiElement, element: PsiElement): List<PsiReference> {
    val scope = LocalSearchScope(element)
    return ReferencesSearch.search(def, scope).findAll().toList()
}

/**
 * Extract reference to function from long name.
 * Example:
 * `Module.A.func` => reference to `func`
 */
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

private fun convertCallToConcrete(call: PsiElement, scope: Scope): Concrete.AppExpression? {
    var convertedExpression = ConcreteBuilder.convertExpression(call as Abstract.Expression)
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
    if (convertedExpression is Concrete.LamExpression)
        convertedExpression = convertedExpression.body // expression contained implicit lambda "__"
    return convertedExpression as? Concrete.AppExpression ?: return null
}

/**
 * Returns original text(as in editor, not pretty-printing) for concrete
 */
private fun textOfConcreteAppExpression(concrete: Concrete.AppExpression): String =
    buildString {
        val function = concrete.function.data as PsiElement
        val call = function.ancestor<ArendArgumentAppExpr>() ?: function
        val functionText = function.text.replace("`", "")
        append("$functionText ")

        for (arg in concrete.arguments) {
            val concreteArg = arg.expression
            fun isLiteral (p : Any?) : Boolean = (p as? ArendLiteral)?.applyHole != null
            var parens = false
            val argText =
                if (concreteArg is Concrete.LamExpression &&
                    (isLiteral(concreteArg.data) || (concreteArg.data as? ArendArgumentAppExpr)?.argumentList?.any { isLiteral((it as? ArendAtomArgument)?.atomFieldsAcc?.atom?.literal) } == true)) {
                    (concreteArg.body.data as PsiElement).text
                } else if (concreteArg !is Concrete.AppExpression) {
                    if (concreteArg is Concrete.ReferenceExpression) {
                        parens = concreteArg.referent is GlobalReferable && (concreteArg.referent as GlobalReferable).representablePrecedence.isInfix
                    }
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
                if (needToWrapInBrackets(argText) || parens) {
                    append("($argText) ")
                } else {
                    append("$argText ")
                }
            } else {
                val surroundedWithBrackets = argText.trim().let { it.isNotEmpty() && it.first() == '{' && it.last() == '}' }
                if (!surroundedWithBrackets) {
                    append("{$argText} ")
                } else {
                    append("$argText ")
                }
            }
        }
    }.trimEnd()

/**
 * Returns child of `parent` which contains `element` inside
 */
private fun getTopChildOnPath(element: PsiElement, parent: PsiElement): PsiElement {
    var current = element
    while (current.parent != null && current.parent != parent) {
        current = current.parent
    }
    return current
}

/**
 * @param topCall  call that the `concrete`-expression is inside
 * @return  psi-range corresponding to this `concrete`
 */
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

/**
 * Returns text for psi-range. First and last must have the same parent
 */
private fun getTextForRange(first: PsiElement, last: PsiElement) = buildString {
    var current = first
    append(current.text)
    while (current != last) {
        current = current.nextSibling
        append(current.text)
    }
}

private fun inOpenDeclaration(usage: PsiElement): Boolean = usage.ancestor<ArendStatCmd>()?.openKw != null
