package org.arend.refactoring.changeSignature.entries

import org.arend.codeInsight.ArendCodeInsightUtils.Companion.getThisParameter
import org.arend.codeInsight.ParameterDescriptor
import org.arend.codeInsight.SignatureUsageContext
import org.arend.ext.module.LongName
import org.arend.ext.reference.Precedence
import org.arend.ext.variable.Variable
import org.arend.ext.variable.VariableImpl
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.Referable
import org.arend.naming.renamer.StringRenamer
import org.arend.psi.ancestor
import org.arend.psi.ancestors
import org.arend.psi.childOfType
import org.arend.psi.ext.*
import org.arend.quickfix.referenceResolve.ResolveReferenceAction
import org.arend.refactoring.changeSignature.*
import org.arend.resolving.ArendReference
import org.arend.term.concrete.Concrete
import java.util.*
import java.util.Collections.singletonList
import kotlin.collections.ArrayList

abstract class UsageEntry(val refactoringContext: ChangeSignatureRefactoringContext,
                          val contextPsi: ArendCompositeElement,
                          val descriptor: ChangeSignatureRefactoringDescriptor?,
                          val target: Referable?) {
    private val usageContext = SignatureUsageContext.getParameterContext(contextPsi)

    abstract fun getArguments(): List<ArgumentPrintResult>

    private fun getTrailingParameters(): List<ParameterDescriptor> {
        val result = ArrayList<ParameterDescriptor>()
        val newParameters = getNewParameters()
        for (newParam in newParameters.reversed()) if (newParam.isExplicit != newParam.oldParameter?.isExplicit) break else {
            result.add(newParam.oldParameter)
        }
        return result
    }

    open fun getLambdaParams(parameterMap: Set<ParameterDescriptor>, includingSuperfluousTrailingParams: Boolean): List<ParameterDescriptor> {
        val lambdaParameters = ArrayList<ParameterDescriptor>(getOldParameters().filter { !it.isThis() && !it.isExternal() })
        lambdaParameters.removeAll(parameterMap)
        if (!includingSuperfluousTrailingParams) lambdaParameters.removeAll(getTrailingParameters().toSet())
        return lambdaParameters
    }

    fun getOldParameters(): List<ParameterDescriptor> {
        return usageContext.filterParameters(descriptor!!.oldParameters)
    }

    fun getNewParameters(): List<ParameterDescriptor> {
        return usageContext.filterParameters(descriptor!!.newParameters)
    }

    abstract fun getContextName(): String

    fun printUsageEntry(globalReferable: GlobalReferable? = null): IntermediatePrintResult {
        val doubleBuilder = DoubleStringBuilder()
        val oldParameters = getOldParameters()
        val newParameters = getNewParameters()

        var i = 0
        var j = 0
        val parameterMap = HashMap<ParameterDescriptor, ArgumentPrintResult?>()
        while (i < oldParameters.size && j < getArguments().size) {
            val param = oldParameters[i]
            val arg = getArguments()[j]
            if (arg.isExplicit == param.isExplicit) {
                parameterMap[param] = arg
                i++
                j++
            } else if (!param.isExplicit) {
                parameterMap[param] = null
                i++
            }
            else throw IllegalArgumentException()
        }

        val hasExplicitExternalArgument = parameterMap.keys.filter { it.isExternal() }.any { parameterMap[it] != null }

        if (newParameters.isNotEmpty() &&
            newParameters.first().oldParameter?.isThis() == true &&
            descriptor?.moveRefactoringContext != null &&
            parameterMap[newParameters.first().oldParameter] == null) {
            val thisName = contextPsi.ancestors.filterIsInstance<PsiLocatedReferable>().mapNotNull { descriptor.moveRefactoringContext.myThisVars[it] }.firstOrNull()
            if (thisName != null)
            parameterMap[newParameters.first().oldParameter!!] =
                ArgumentPrintResult(IntermediatePrintResult(thisName, thisName, true, false, globalReferable), false, " ")
        }

        val (isAtomicExpression, isLambda) = printUsageEntryInternal(globalReferable, newParameters, parameterMap, hasExplicitExternalArgument, j, doubleBuilder)
        val normalText = doubleBuilder.defaultBuilder.toString()
        val parenthesizedPrefixText = doubleBuilder.alternativeBuilder.toString()

        return IntermediatePrintResult(
            normalText,
            if (!isLambda) parenthesizedPrefixText else null,
            isAtomicExpression && !isLambda,
            isLambda,
            globalReferable)
    }

    open fun printUsageEntryInternal(
        globalReferable: GlobalReferable?,
        newParameters: List<ParameterDescriptor>,
        parameterMap: MutableMap<ParameterDescriptor, ArgumentPrintResult?>,
        hasExplicitExternalArgument: Boolean,
        argumentStartIndex: Int,
        doubleBuilder: DoubleStringBuilder): Pair<Boolean, Boolean> {
        val defClassMode = descriptor?.getAffectedDefinition() is ArendDefClass
        val lambdaParams = getLambdaParams(parameterMap.keys, false)
        val oldArgToLambdaArgMap = HashMap<ParameterDescriptor, String>()
        var lambdaArgs = ""
        val referables = ArrayList<Variable>()

        if (lambdaParams.isNotEmpty()) {
            val context = contextPsi.scope.elements.map { VariableImpl(it.textRepresentation()) }
            lambdaParams.forEach {
                val freshName = StringRenamer().generateFreshName(VariableImpl(it.getNameOrUnderscore()), context + referables)
                referables.add(VariableImpl(freshName))
                lambdaArgs += if (it.isExplicit) " $freshName" else " {$freshName}"
                oldArgToLambdaArgMap[it] = freshName
            }
        }

        val isLambda = lambdaArgs != "" && !defClassMode && this !is PatternEntry
        if (isLambda) doubleBuilder.append("\\lam$lambdaArgs => ")
        for (e in oldArgToLambdaArgMap) parameterMap[e.key] =
            ArgumentPrintResult(IntermediatePrintResult(if (this is PatternEntry) "_" else e.value, null, true, false, null), true, null)

        val printedFirstParameter = newParameters.firstOrNull()?.oldParameter?.let { parameterMap[it]?.printResult }
        val dotNotationSupported = newParameters.isNotEmpty() && newParameters.first().isThis() && printedFirstParameter?.text?.let { isIdentifier(it) } == true
                && target !is ArendConstructor /* <-- This is due to a small bug in Arend scopes */

        val startIndex = if (printedFirstParameter != null && dotNotationSupported && descriptor?.moveRefactoringContext == null) {
            doubleBuilder.append(printedFirstParameter.text)
            doubleBuilder.append(".")
            doubleBuilder.append(target?.refName!!)
            1
        } else {
            val contextName = getContextName()
            doubleBuilder.append(contextName, if (globalReferable?.precedence?.isInfix == true) "($contextName)" else contextName)
            0
        }

        val lastParameter = newParameters.lastOrNull { parameterMap[it.oldParameter] != null && !oldArgToLambdaArgMap.contains(it.oldParameter) }
        val lastIndex = if (lastParameter != null) newParameters.indexOf(lastParameter) else -1
        val relevantSegment = if (defClassMode) newParameters.subList(startIndex, lastIndex + 1) else newParameters.subList(startIndex, newParameters.size)
        var isAtomicExpression = !printParams(globalReferable, relevantSegment, lambdaParams, parameterMap, hasExplicitExternalArgument, doubleBuilder)

        var j = argumentStartIndex
        while (j < getArguments().size) {
            doubleBuilder.append(" ${getArguments()[j].printResult.text}")
            j++
            isAtomicExpression = false
        }

        return Pair(isAtomicExpression, isLambda)
    }

    protected fun printParam(globalReferable: GlobalReferable?,
                             oldParam: ParameterDescriptor?,
                             newParam: ParameterDescriptor,
                             parameterMap: Map<ParameterDescriptor, ArgumentPrintResult?>,
                             hasExplicitExternalArgument: Boolean,
                             parameterInfo: RenderedParameterKind? = null,
                             commentedText: String? = null): PrintedParameter {
        val parameter = oldParam?.let{ parameterMap[oldParam] }
        val referable = parameter?.printResult?.referable
        val inhibitParens = if (referable != null && parameterInfo != null && globalReferable != null) {
            if (referable == globalReferable) {
                parameterInfo == RenderedParameterKind.INFIX_LEFT && referable.precedence.associativity == Precedence.Associativity.LEFT_ASSOC ||
                        parameterInfo == RenderedParameterKind.INFIX_RIGHT && referable.precedence.associativity == Precedence.Associativity.RIGHT_ASSOC
            } else {
                referable.precedence.priority > globalReferable.precedence.priority
            }
        } else false

        val refactoringContext = descriptor?.moveRefactoringContext
        val thisText = if ((newParam.isThis() || oldParam?.isThis() == true) && refactoringContext != null && (parameter == null || parameter.printResult.text == "_")) {
            val parameterClass = newParam.getThisDefClass() ?: oldParam?.getThisDefClass()
            if (parameterClass != null) {
                val contextReferable = contextPsi.ancestor<PsiLocatedReferable>()
                val contextClass = contextReferable?.ancestors?.mapNotNull { refactoringContext.membersEnvelopingClasses[it] }?.firstOrNull()
                    ?: if (contextReferable != null) { getThisParameter(contextReferable)?.getThisDefClass() } else null

                when {
                    commentedText == "_" || commentedText == "{?}" || commentedText == "\\this" ->
                        "{?}"
                    commentedText != null ->
                        "{?} {-${commentedText}-}"
                    contextClass != null && contextClass.isSubClassOf(parameterClass) ->
                        "\\this"
                    contextClass != null -> "{?}"
                    else -> "_"
                }
            } else null
        } else null

        val (text, requiresParentheses) = when {
            thisText != null -> Pair(thisText, false)
            (oldParam == null) -> Pair("{?}", false)
            (oldParam.isExternal() && parameter == null && oldParam.getExternalScope()?.let{ usageContext.envelopingGroups.contains(it) } == true && !hasExplicitExternalArgument) ->
                Pair(oldParam.getNameOrUnderscore(), false)
            (parameter == null) -> Pair("_", false)
            else -> Pair(if (parameterInfo == RenderedParameterKind.INFIX_RIGHT && parameter.printResult.parenthesizedPrefixText != null) parameter.printResult.parenthesizedPrefixText else
                parameter.printResult.text, !parameter.printResult.isAtomic && !parameter.printResult.isLambda && !inhibitParens)
        }

        val result = if (newParam.isExplicit) (if (requiresParentheses) "(${text})" else text) else (if (text.startsWith("-")) "{ ${text}}" else "{${text}}")
        val spacingText = parameter?.spacingText ?: " "
        return PrintedParameter(result, spacingText)
    }

   protected fun printParams(globalReferable: GlobalReferable?,
                    params: List<ParameterDescriptor>,
                    lambdaParams: List<ParameterDescriptor>,
                    parameterMap: Map<ParameterDescriptor, ArgumentPrintResult?>,
                    hasExplicitExternalArgument: Boolean,
                    builder: DoubleStringBuilder): Boolean {
        var implicitArgPrefix = ""
        var spacingContents = ""
        var somethingWasPrinted = false
        val oldThisParam = parameterMap.keys.firstOrNull{ it.isThis() }

        for (newParam in params) {
            val oldParam = newParam.oldParameter
            if (!lambdaParams.contains(oldParam) && getLambdaParams(parameterMap.keys, true).contains(oldParam)) break

            val commentedText = if (oldThisParam != null && newParam.isThis() && newParam.oldParameter == null && params.all { it.oldParameter?.isThis() != true }) parameterMap[oldThisParam]?.printResult?.text else null
            val (text, spacing) = printParam(globalReferable, oldParam, newParam, parameterMap, hasExplicitExternalArgument, null, commentedText)

            if (text == "{_}") {
                implicitArgPrefix += spacing + text
                spacingContents += (if (spacing.endsWith(" ")) spacing.trimEnd() else spacing)
            } else {
                somethingWasPrinted = true
                if (!newParam.isExplicit) {
                    builder.append(implicitArgPrefix)
                } else {
                    builder.append(spacingContents)
                }
                builder.append(spacing + text)
                implicitArgPrefix = ""
                spacingContents = ""
            }
        }

       return somethingWasPrinted
    }

    companion object {
        enum class RenderedParameterKind {INFIX_LEFT, INFIX_RIGHT}

        data class PrintedParameter(val text: String, val spacing: String)

        fun getContextName(affectedDefinition: PsiLocatedReferable, contextPsi: ArendCompositeElement, refactoringContext: ChangeSignatureRefactoringContext): String {
            val data = ResolveReferenceAction.getTargetName(affectedDefinition, contextPsi, refactoringContext.deferredNsCmds)
            val longNameString = data.first
            val namespaceCommand = data.second
            if (namespaceCommand != null) {
                refactoringContext.deferredNsCmds.add(namespaceCommand)
            }
            return longNameString
        }

        fun getContextName(context: ChangeSignatureRefactoringContext, concreteExpr: Concrete.SourceNode): String {
            val referenceExpression = when (concreteExpr) {
                is Concrete.AppExpression -> concreteExpr.function.data
                is Concrete.ReferenceExpression -> concreteExpr.data
                is Concrete.ConstructorPattern -> concreteExpr.constructorData
                else -> null
            }

            val referent = when (concreteExpr) {
                is Concrete.ReferenceExpression -> concreteExpr.referent
                is Concrete.AppExpression -> (concreteExpr.function as? Concrete.ReferenceExpression)?.referent
                is Concrete.ConstructorPattern -> concreteExpr.constructor
                else -> null
            }

            val referenceList: List<ArendReference> = when (referenceExpression) {
                is ArendLongName -> referenceExpression.refIdentifierList.mapNotNull { it.reference }
                is ArendPattern -> referenceExpression.childOfType<ArendLongName>()?.refIdentifierList?.mapNotNull { it.reference } ?:
                    (referenceExpression.singleReferable?.reference?.let { singletonList(it) }) ?: emptyList()
                else -> emptyList()
            }

            val longName: List<String> = when (referenceExpression) {
                is ArendLongName -> referenceExpression.longName
                is ArendPattern -> referenceList.map { it.element.text }
                is ArendIPName -> referenceExpression.longName
                else -> emptyList()
            }

            var resolvesOk = true
            for (i in 0..referenceList.size - 2) {
                val ref1 = referenceList[i].resolve() as? ArendGroup
                val ref2 = referenceList[i+1].resolve() as? PsiLocatedReferable
                if (ref1 == null || ref2 == null || !(ref1.dynamicSubgroups.contains(ref2) || ref1.internalReferables.contains(ref2) || ref1.statements.any { it.group == ref2 })) {
                    resolvesOk = false
                }
            }

            if (!resolvesOk && referent is PsiLocatedReferable && referenceExpression is ArendCompositeElement) {
                return getContextName(referent, referenceExpression, context)
            }

            return getCorrectedContextName(context, referenceList.zip(longName))
        }

        fun getCorrectedContextName(context: ChangeSignatureRefactoringContext,
                                    longName: List<Pair<ArendReference, String>>): String =
            LongName(longName.map { (ref, name) ->
                (ref.resolve() as? Referable)?.let { context.identifyDescriptor(it) }?.let {
                    if (name == it.getAffectedDefinition()?.name) it.newName else name } ?: name}).toString()
    }

}