package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.TokenType
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.arend.codeInsight.ArendParameterInfoHandler.Companion.findParamIndex
import org.arend.codeInsight.ArendParameterInfoHandler.Companion.getAllParametersForReferable
import org.arend.ext.variable.Variable
import org.arend.ext.variable.VariableImpl
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.renamer.StringRenamer
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.util.ArendBundle
import org.arend.psi.ext.PsiReferable
import org.arend.refactoring.*
import org.arend.resolving.ArendReferableConverter
import org.arend.term.abs.Abstract
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.PrettyPrintVisitor
import org.arend.util.appExprToConcrete
import org.arend.util.getBounds
import java.lang.IllegalArgumentException
import java.lang.StringBuilder

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
        if (DumbService.isDumb(element.project)) return false

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
        val indexInCons = if (def is ArendConstructor) getTeleIndexInDef(def, element, false) + indexInTele else -1
        val fCalls = ReferencesSearch.search(def).map { it.element }.filter { it.isValid }
        val insertedPsi = ArrayList<PsiElement>()

        for (fCall in fCalls.map { getParentPsiFunctionCall(it) }.sortedBy { it.textLength })
            if (fCall is ArendArgumentAppExpr) {
                CallWrapper.wrapWithSubTerms(fCall, def, insertedPsi)
            }

        val updatedCalls = ArrayList(fCalls)
        insertedPsi.filter { it.isValid }.map { call -> updatedCalls.addAll(searchRefsInPsiElement(def, call).map { it.element }) }

        for (ref in updatedCalls.filter{ it.isValid && !inOpenDeclaration(it) }) {
            replaceWithSubTerms(ref, def, if (def is ArendConstructor && ref is ArendDefIdentifier && ref.parent is ArendPattern) indexInCons else indexInDef)
        }

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
            if (ref.element.isValid && ref.element != callerRef?.element) {
                replaceWithSubTerms(ref.element, def, indexInDef)
            }
        }

        // after wrapping in parens it should be true for most cases, because we shouldn't go up a lot
        if (callerRef != null && def == callerRef.resolve() /* resolveCaller(updatedCall)*/) {
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
        val isCostructorInPattern = def is ArendConstructor && call is ArendPattern
        val parameters = getAllParametersForReferable(def as PsiReferable, !isCostructorInPattern)
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
            if (call is Abstract.Expression) {
                if (lastImplicitArgumentsAreOmitted(def, lastIndex)) {
                    insertArgumentInIndex(call, createArgument("_"), argsText.size)
                    return call
                }
                return rewriteToLambda(call, def, indexInDef, lastIndex)
            } else if (call is ArendPattern) return call // We are attempting to refactor code with "Not enough patterns" error?
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
            changeArgumentInIndex(call, newArgText, def, indexInArgs)
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
    private fun insertArgumentInIndex(call: PsiElement, argument: PsiElement, index: Int) { //TODO: Implement me for patterns!!!
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
    private fun changeArgumentInIndex(call: PsiElement, newArgText: String, def: PsiElement, index: Int) {
        val argument = if (call is ArendPattern) ArendPsiFactory(call.project).createAtomPattern(newArgText) else createArgument(newArgText)
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

    abstract fun getContext(element: PsiElement): List<Variable>

    /**
     * Extracts reference to the function of given call
     */
    abstract fun extractRefIdFromCalling(def: PsiElement, call: PsiElement): PsiReference?

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
        if (element.parent is ArendPattern || element.parent is ArendLongName && element.parent.parent is ArendPattern) element.ancestor<ArendPattern>()!! else
        element.ancestor<ArendArgumentAppExpr>() ?: element

    override fun convertCallToPrefix(call: PsiElement): PsiElement? {
        if (call !is Abstract.Expression) return null
        val rangesMap = HashMap<Concrete.Expression, TextRange>()
        val concrete = if (call is ArendArgumentAppExpr) CallWrapper.convertToConcreteAndGetBounds(call, rangesMap) else null
        val functionCallText = if (concrete is Concrete.AppExpression) textOfConcreteAppExpression(concrete, rangesMap) else return null
        return factory.createArgumentAppExpr(functionCallText)
    }

    override fun getCallingParameters(call: PsiElement): List<PsiElement> = when (call) {
        is ArendArgumentAppExpr -> call.argumentList
        is ArendPattern -> call.atomPatternOrPrefixList
        else -> throw IllegalArgumentException()
    }

    override fun getContext(element: PsiElement): List<Variable> {
        val argumentAppExpr = element as ArendArgumentAppExpr
        return argumentAppExpr.scope.elements.map { VariableImpl(it.textRepresentation()) }
    }

    override fun extractRefIdFromCalling(def: PsiElement, call: PsiElement): PsiReference? {
        val function = when (call) {
            is ArendArgumentAppExpr -> call.atomFieldsAcc
            is ArendPattern -> when {
                call.defIdentifier != null -> call.defIdentifier
                call.longName != null -> call.longName?.refIdentifierList?.last()
                else -> null
            }
            else -> null
        } ?: throw IllegalArgumentException()

        val refs = searchRefsInPsiElement(def, function)
        return if (refs.isEmpty()) null else refs.first()
    }

    override fun getCallingParametersWithPhantom(call: PsiElement): List<String> {
        return when (call) {
            is ArendPattern -> {
                val concretePattern = ConcreteBuilder.convertPattern(call, ArendReferableConverter, null, null)
                concretePattern.patterns.map {
                    val sb = StringBuilder()
                    PrettyPrintVisitor(sb, 0).prettyPrintPattern(it, 0, false)
                    sb.toString().let { str -> if (it.isExplicit) str else "{${str}}" }
                }
            }
            is Abstract.Expression -> {
                val concrete = appExprToConcrete(call) as? Concrete.AppExpression
                concrete?.arguments?.map { if (it.isExplicit) it.toString() else "{$it}" } ?: emptyList()
            }
            else -> throw IllegalArgumentException()
        }
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

    override fun extractRefIdFromCalling(def: PsiElement, call: PsiElement): PsiReference? {
        val function = (call as ArendLocalCoClause).longName!!
        val refs = searchRefsInPsiElement(def, function)
        return if (refs.isEmpty()) null else refs.first()
    }

    override fun getCallingParametersWithPhantom(call: PsiElement): List<String> {
        return getCallingParameters(call).map { it.text }
    }

    override fun createArgument(arg: String): PsiElement = factory.createCoClause("dummy $arg").lamParamList.first()
}

private object CallWrapper {
    private val wrapped = mutableSetOf<PsiElement>()
    private lateinit var factory: ArendPsiFactory

    /**
        Wraps into parens all calls in `call`-element where resolve of function equals `def`
    */

    fun convertToConcreteAndGetBounds(psi: ArendArgumentAppExpr, rangesMap: HashMap<Concrete.Expression, TextRange>): Concrete.Expression? {
        val cExpr = appExprToConcrete(psi)
        val children = psi.node.getChildren(null).filter { it.elementType != TokenType.WHITE_SPACE }.toList()
        if (cExpr != null) getBounds(cExpr, children, rangesMap)
        return cExpr
    }

    fun wrapWithSubTerms(call: ArendArgumentAppExpr, def: PsiElement, insertedPsi: MutableList<PsiElement>) {
        val rangeData = HashMap<Concrete.Expression, TextRange>()
        factory = ArendPsiFactory(def.project)
        val concrete = convertToConcreteAndGetBounds(call, rangeData)

        fun wrapInArguments(appExpr: Concrete.Expression, def: PsiElement) {
            if (appExpr is Concrete.AppExpression) {
                val function = appExpr.function.data as PsiElement
                val resolve = tryResolveFunctionName(function)

                for (argument in appExpr.arguments) {
                    wrapInArguments(argument.expression, def)
                }

                if (def == resolve) {
                    val atomFieldsAcc = function.parent?.parent?.parent
                    if (function is ArendIPName && function.postfix != null && atomFieldsAcc is ArendAtomFieldsAcc) {
                        val transformedAppExpr = transformPostfixToPrefix(factory, atomFieldsAcc, function, appExpr, rangeData) { range, i -> updateTextRanges(rangeData, range, i) }
                        if (transformedAppExpr is PsiElement) insertedPsi.add(transformedAppExpr)
                    } else
                        wrapCall(appExpr, insertedPsi, rangeData)
                }

            } else if (appExpr is Concrete.LamExpression && appExpr.data == null) { //postfix or apply hole expression
                wrapInArguments(appExpr.body, def)
            }
        }

        if (concrete != null) wrapInArguments(concrete, def)
    }

    fun updateTextRanges(rangeData: HashMap<Concrete.Expression, TextRange>, modifiedTextRange: TextRange, insertedLength: Int) {
        val lenDiff = insertedLength - modifiedTextRange.length
        for (rangeEntry1 in rangeData.entries.toList()) {
            if (rangeEntry1.value.contains(modifiedTextRange)) {
                rangeData[rangeEntry1.key] = TextRange(rangeEntry1.value.startOffset, rangeEntry1.value.endOffset + lenDiff)
            } else if (rangeEntry1.value.startOffset > modifiedTextRange.endOffset) {
                rangeData[rangeEntry1.key] = rangeEntry1.value.shiftRight(lenDiff)
            } else if (rangeEntry1.value.endOffset < modifiedTextRange.startOffset) {
                //Do nothing
            } else if (modifiedTextRange.contains(rangeEntry1.value)) {
                rangeData.remove(rangeEntry1.key) // information no longer valid
            } else if (rangeEntry1.value.endOffset > modifiedTextRange.startOffset) {
                throw java.lang.IllegalStateException()
            }
        }
    }

    /**
     * Wraps the psi-call corresponding `concrete.data`
     *
     * @return  concrete, where call is wrapped
     */
    fun wrapCall(concrete: Concrete.AppExpression, insertedPsi: MutableList<PsiElement>, rangeData: HashMap<Concrete.Expression, TextRange>) {
        val call = (concrete.function.data as PsiElement).ancestor<ArendArgumentAppExpr>() ?: return
        val (first, last) = getRangeForConcrete(concrete, call, rangeData) ?: return
        val isCurrentCallOnTop = (call.firstChild == first && call.lastChild == last)
        val isAlreadyWrapped =
            wrapped.contains(call) ||
                    !call.isValid ||
                    ("(${call.text})" == call.ancestor<ArendTuple>()?.text && isCurrentCallOnTop)

        if (isAlreadyWrapped) return

        val callText = textOfConcreteAppExpression(concrete, rangeData)
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
        updateTextRanges(rangeData, TextRange(first.textRange.startOffset, last.textRange.endOffset), wrappedCall.textLength)

        val insertedCall = call.addAfterWithNotification(wrappedCall, last)
        call.deleteChildRangeWithNotification(first, last)
        wrapped.add(insertedCall.childOfType<ArendArgumentAppExpr>()!!)
        insertedPsi.add(insertedCall)
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
        if (needToWrapInParens(text)) {
            newText = "($newText)"
        }
        return newText
    }

    return if (!toImplicit) {
        if (text.isNotEmpty() && text.first() == '-') "{ $text}" else "{$text}"
    } else {
        newText = if (text == "()") text else text.substring(1, text.length - 1)
        if (newText.isNotEmpty() && newText.first() == '-') "{ $newText}" else "{$newText}"
    }
}

/**
 * Returns first argument's index in this `tele` among all arguments in `def`
 */
private fun getTeleIndexInDef(def: PsiElement, tele: PsiElement, includeConstructorParametersComingFromDefData: Boolean = true): Int {
    val parameters = getAllParametersForReferable(def as PsiReferable, includeConstructorParametersComingFromDefData)

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

private fun needToWrapInParens(expr: String): Boolean {
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

private fun containsApplyHole(expr: Any?) : Boolean =
    (expr as? ArendLiteral)?.applyHole != null ||
            (expr as? ArendArgumentAppExpr)?.argumentList?.any {
                ((it as? ArendAtomArgument)?.atomFieldsAcc?.atom?.literal)?.applyHole != null
            } == true

/**
 * Returns original text(as in editor, not pretty-printing) for concrete
 */
private fun textOfConcreteAppExpression(concrete: Concrete.AppExpression, rangeData: HashMap<Concrete.Expression, TextRange>): String =
    buildString {
        val function = concrete.function.data as PsiElement
        val call = function.ancestor<ArendArgumentAppExpr>() ?: function
        val functionText = function.text.replace("`", "")
        append("$functionText ")

        for (arg in concrete.arguments) {
            val concreteArg = arg.expression
            var needToWrap = false
            val argText =
                if (concreteArg is Concrete.LamExpression && containsApplyHole(concreteArg.data)) {
                    (concreteArg.body.data as PsiElement).text
                } else if (concreteArg !is Concrete.AppExpression) {
                    if (concreteArg is Concrete.ReferenceExpression && concreteArg.referent is GlobalReferable && (concreteArg.referent as GlobalReferable).representablePrecedence.isInfix) {
                        needToWrap = true
                    }
                    (concreteArg.data as PsiElement).text
                } else { // concreteArg is Concrete.AppExpression
                    val (first, last) = getRangeForConcrete(concreteArg, call, rangeData) ?: continue
                    getTextForRange(first, last)
                }

            // avoid duplication in case R.foo <=> foo {R}
            // functionText is `R.foo`, argText is `R.foo`
            if (functionText == argText) {
                continue
            }

            if (arg.isExplicit) {
                if (needToWrapInParens(argText) || needToWrap) {
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
private fun getRangeForConcrete(concrete: Concrete.Expression, topCall: PsiElement, rangeData: HashMap<Concrete.Expression, TextRange>): Pair<PsiElement, PsiElement>? {
    val textRange = rangeData[concrete]
    if (textRange != null) {
        val first1 = topCall.children.first { textRange.contains(it.textRange) }
        val last1 = topCall.children.reversed().first { textRange.contains(it.textRange) }
        return Pair(first1, last1)
    }
    return null
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
