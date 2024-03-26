package org.arend.refactoring.changeSignature.entries

import org.arend.codeInsight.ParameterDescriptor
import org.arend.codeInsight.SignatureUsageContext
import org.arend.ext.module.LongName
import org.arend.ext.reference.Precedence
import org.arend.ext.variable.Variable
import org.arend.ext.variable.VariableImpl
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.Referable
import org.arend.naming.renamer.StringRenamer
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendDefClass
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.quickfix.referenceResolve.ResolveReferenceAction
import org.arend.refactoring.changeSignature.*
import java.util.*
import kotlin.collections.ArrayList

abstract class UsageEntry(val refactoringContext: ChangeSignatureRefactoringContext,
                          val contextPsi: ArendCompositeElement,
                          val descriptor: ChangeSignatureRefactoringDescriptor?,
                          val target: Referable?) {
    val usageContext = SignatureUsageContext.getParameterContext(contextPsi)
    private val myContextName: String

    init {
        val affectedDefinition = descriptor?.affectedDefinition
        if (affectedDefinition is PsiLocatedReferable) {
            val data = ResolveReferenceAction.getTargetName(descriptor?.affectedDefinition as PsiLocatedReferable, contextPsi, refactoringContext.deferredNsCmds)
            val longNameString = data.first
            val namespaceCommand = data.second
            if (namespaceCommand != null) {
                refactoringContext.deferredNsCmds.add(namespaceCommand)
            }

            val longName = LongName.fromString(longNameString).toList()
            myContextName = if (target == affectedDefinition && descriptor.newName != null && longName.last() == affectedDefinition.name) {
                LongName(longName.take(longName.size - 1) + Collections.singletonList(descriptor.newName)).toString()
            } else longNameString
        } else {
            myContextName = ""
        }
    }

    abstract fun getArguments(): List<ArgumentPrintResult>

    private fun getTrailingParameters(): List<ParameterDescriptor> {
        val result = ArrayList<ParameterDescriptor>()
        val newParameters = getParameters().second
        for (newParam in newParameters.reversed()) if (newParam.isExplicit != newParam.oldParameter?.isExplicit) break else {
            result.add(newParam.oldParameter)
        }
        return result
    }

    open fun getLambdaParams(parameterMap: Set<ParameterDescriptor>, includingSuperfluousTrailingParams: Boolean): List<ParameterDescriptor> {
        val lambdaParameters = ArrayList<ParameterDescriptor>(getParameters().first)
        lambdaParameters.removeAll(parameterMap)
        if (!includingSuperfluousTrailingParams) lambdaParameters.removeAll(getTrailingParameters().toSet())
        return lambdaParameters
    }
    abstract fun getParameters(): Pair<List<ParameterDescriptor>, List<ParameterDescriptor>>
    open fun getContextName(): String = myContextName

    open fun getUnmodifiablePrefix(): String? = null
    open fun getUnmodifiableSuffix(): String? = null

    /* TODO: Get rid of explicit casts to descendants of UsageEntry, add & use class methods instead */
    fun printUsageEntry(globalReferable: GlobalReferable? = null) :
            Pair<String /* default version */, String? /* version with parenthesized operator; makes sense only for AppExpr */ > {
        val defaultBuilder = StringBuilder()
        val parenthesizedPrefixBuilder = StringBuilder()
        fun append(text: String) { defaultBuilder.append(text); parenthesizedPrefixBuilder.append(text) }

        val (oldParameters, newParameters) = getParameters()

        getUnmodifiablePrefix()?.let { defaultBuilder.append(it); parenthesizedPrefixBuilder.append(it) }

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

        val context = contextPsi.scope.elements.map { VariableImpl(it.textRepresentation()) }
        val referables = ArrayList<Variable>()
        val oldArgToLambdaArgMap = HashMap<ParameterDescriptor, String>()
        var lambdaArgs = ""
        val lambdaParams = getLambdaParams(parameterMap.keys, false)

        lambdaParams.map {
            val freshName = StringRenamer().generateFreshName(VariableImpl(it.getNameOrUnderscore()), context + referables)
            referables.add(VariableImpl(freshName))
            lambdaArgs += if (it.isExplicit) " $freshName" else " {$freshName}"
            oldArgToLambdaArgMap[it] = freshName
        }

        val infixNotationSupported = newParameters.withIndex().filter { it.value.isExplicit }.let { explicitParams -> explicitParams.size == 2 && explicitParams.all { it.index >= newParameters.size - 2 } } &&
                globalReferable?.precedence?.isInfix == true
        val hasExplicitExternalArgument = parameterMap.keys.filter { it.isExternal() }.any { parameterMap[it] != null }

        fun renderParameter(oldParam: ParameterDescriptor?, newParam: ParameterDescriptor, parameterInfo: RenderedParameterKind? = null): Pair<String /* text */, String /* spacing */> {
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

            val (text, requiresParentheses) = when {
                (oldParam == null) -> Pair("{?}", false)
                (oldParam.isExternal() && parameter == null && oldParam.getExternalScope()?.let{ usageContext.envelopingGroups.contains(it) } == true && !hasExplicitExternalArgument) ->
                    Pair(oldParam.getNameOrUnderscore(), false)
                (parameter == null) -> Pair("_", false)
                else -> Pair(if (parameterInfo == RenderedParameterKind.INFIX_RIGHT && parameter.printResult.parenthesizedPrefixText != null) parameter.printResult.parenthesizedPrefixText else
                    parameter.printResult.strippedText ?: parameter.printResult.text, !parameter.printResult.isAtomic && !inhibitParens)
            }

            val result = if (newParam.isExplicit) (if (requiresParentheses) "(${text})" else text) else (if (text.startsWith("-")) "{ ${text}}" else "{${text}}")
            val spacingText = parameter?.spacingText ?: " "
            return Pair(result, spacingText)
        }

        fun printParams(params: List<ParameterDescriptor>) {
            var implicitArgPrefix = ""
            var spacingContents = ""
            for ((index, newParam) in params.withIndex()) {
                val oldParam = newParam.oldParameter
                if (!lambdaParams.contains(oldParam) && getLambdaParams(parameterMap.keys, true).contains(oldParam)) break

                if (newParam.getThisDefClass() != null && newParam.getThisDefClass() == usageContext.envelopingDynamicClass) {
                    val nextParam = params.getOrNull(index+1)
                    if (nextParam != null && nextParam.isExplicit) continue // No need to write implicit {this} argument inside the dynamic part of a class
                }

                val (text, spacing) = renderParameter(oldParam, newParam)

                if (text == "{_}") {
                    implicitArgPrefix += spacing + text
                    spacingContents += (if (spacing.endsWith(" ")) spacing.trimEnd() else spacing)
                } else {
                    if (!newParam.isExplicit) {
                        append(implicitArgPrefix)
                    } else {
                        append(spacingContents)
                    }
                    append(spacing + text)
                    implicitArgPrefix = ""
                    spacingContents = ""
                }
            }
        }

        if (infixNotationSupported && (this is AppExpressionEntry || this is PatternEntry) && lambdaArgs == "") {
            val (leftParam, rightParam) = newParameters.filter { it.isExplicit }.toList().let { Pair(it[0], it[1]) }
            val (leftText, leftSpacing) = renderParameter(leftParam.oldParameter, leftParam, RenderedParameterKind.INFIX_LEFT)
            val (rightText, rightSpacing) = renderParameter(rightParam.oldParameter, rightParam, RenderedParameterKind.INFIX_RIGHT)

            val contextName = getContextName()  //TODO: Fixme; we should take backticks into account
            append("$leftText$leftSpacing$contextName")

            printParams(newParameters.filter { !it.isExplicit })
            append("$rightSpacing$rightText")
        } else {
            val defClassMode = descriptor?.affectedDefinition is ArendDefClass

            if (lambdaArgs != "" && !defClassMode && this !is PatternEntry) append("\\lam$lambdaArgs => ")
            for (e in oldArgToLambdaArgMap) parameterMap[e.key] =
                ArgumentPrintResult(IntermediatePrintResult(if (this is PatternEntry) "_" else e.value, null, null, true, null), true, null)

            val contextName = getContextName()
            defaultBuilder.append(contextName); parenthesizedPrefixBuilder.append(if (globalReferable?.precedence?.isInfix == true) "($contextName)" else contextName)

            val lastParameter = newParameters.lastOrNull { parameterMap[it.oldParameter] != null && !oldArgToLambdaArgMap.contains(it.oldParameter) }
            val lastIndex = if (lastParameter != null) newParameters.indexOf(lastParameter) else -1
            val relevantSegment = if (defClassMode) newParameters.subList(0, lastIndex + 1) else newParameters
            printParams(relevantSegment)

            while (j < getArguments().size) {
                append(" ${getArguments()[j].printResult.text}")
                j++
            }
        }

        getUnmodifiableSuffix()?.let {
            append(it)
        }
        return Pair(defaultBuilder.toString(), if (lambdaArgs == "") parenthesizedPrefixBuilder.toString() else null)
    }

    companion object {
        private enum class RenderedParameterKind {INFIX_LEFT, INFIX_RIGHT}
    }

}