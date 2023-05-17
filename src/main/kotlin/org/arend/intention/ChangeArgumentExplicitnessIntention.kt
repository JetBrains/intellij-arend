package org.arend.intention

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.elementType
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.containers.SortedList
import org.arend.codeInsight.ArendParameterInfoHandler.Companion.getAllParametersForReferable
import org.arend.error.CountingErrorReporter
import org.arend.error.DummyErrorReporter
import org.arend.ext.error.GeneralError
import org.arend.ext.reference.Precedence
import org.arend.ext.variable.Variable
import org.arend.ext.variable.VariableImpl
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.Referable
import org.arend.naming.renamer.StringRenamer
import org.arend.naming.scope.ClassFieldImplScope
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.ext.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendExpr
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.PsiReferable
import org.arend.quickfix.referenceResolve.ResolveReferenceAction
import org.arend.refactoring.*
import org.arend.refactoring.changeSignature.ArendChangeInfo
import org.arend.refactoring.changeSignature.ArendParameterInfo
import org.arend.refactoring.changeSignature.fixElim
import org.arend.resolving.ArendReferableConverter
import org.arend.resolving.DataLocatedReferable
import org.arend.search.ClassDescendantsSearch
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.PrettyPrintVisitor
import org.arend.util.*
import org.arend.util.getBounds
import java.util.Collections.singletonList

data class Parameter(val isExplicit: Boolean, val referable: Referable?)
data class NewParameter(val isExplicit: Boolean, val oldParameter: Parameter?)
data class ChangeSignatureRefactoringDescriptor(val affectedDefinition: PsiReferable,
                                                val oldParameters: List<Parameter>,
                                                val newParameters: List<NewParameter>,
                                                val oldParametersConstructor: List<Parameter>? = null,
                                                val newParametersConstructor: List<NewParameter>? = null)
data class ChangeSignatureRefactoringContext(val descriptor: ChangeSignatureRefactoringDescriptor,
                                             val textReplacements: HashMap<PsiElement, Pair<String, String?>>,
                                             val rangeData: HashMap<Concrete.SourceNode, TextRange>) {
    fun textGetter(psi: PsiElement): String =
        textReplacements[psi]?.let { it.second ?: it.first } ?: buildString {
            if (psi.firstChild == null) append(psi.text) else
                for (c in psi.childrenWithLeaves.toList()) append(textGetter(c))
        }

}

data class ConcreteDataItem(val psi: PsiElement,
                            val refactoringDescriptor: ChangeSignatureRefactoringDescriptor,
                            val concreteExpr: Concrete.SourceNode?)
data class IntermediatePrintResult(val text: String,
                                   val strippedText: String?,
                                   val parenthesizedPrefixText: String?,
                                   val isAtomic: Boolean,
                                   val referable: GlobalReferable?)
data class ArgumentPrintResult(val printResult: IntermediatePrintResult,
                               val isExplicit: Boolean,
                               val spacingText: String?)

abstract class UsageEntry(val refactoringContext: ChangeSignatureRefactoringContext, val contextPsi: ArendCompositeElement) {
    abstract fun getArguments(): List<ArgumentPrintResult>

    private fun getTrailingParameters(): List<Parameter> {
        val result = ArrayList<Parameter>()
        val newParameters = getParameters().second
        for (newParam in newParameters.reversed()) if (newParam.isExplicit != newParam.oldParameter?.isExplicit) break else {
            result.add(newParam.oldParameter)
        }
        return result
    }

    open fun getLambdaParams(parameterMap: Set<Parameter>, includingSuperfluousTrailingParams: Boolean): List<Parameter> {
        val lambdaParameters = ArrayList<Parameter>(getParameters().first)
        lambdaParameters.removeAll(parameterMap)
        if (!includingSuperfluousTrailingParams) lambdaParameters.removeAll(getTrailingParameters().toSet())
        return lambdaParameters
    }
    abstract fun getParameters(): Pair<List<Parameter>, List<NewParameter>>
    abstract fun getContextName(): String

    open fun getUnmodifiablePrefix(): String? = null
    open fun getUnmodifiableSuffix(): String? = null
}

const val FUNCTION_INDEX = -1

abstract class BinOpEntry<ET : Concrete.SourceNode, PT>(val expr: Concrete.SourceNode, contextPsi: ArendCompositeElement, protected val contextSpecificName: String, refactoringContext: ChangeSignatureRefactoringContext):
    UsageEntry(refactoringContext, contextPsi) {
    private val exprPsi: List<PsiElement>? = refactoringContext.rangeData[expr]?.let { range ->
        contextPsi.childrenWithLeaves.toList().filter { range.contains(it.textRange) }
    }
    val blocks: ArrayList<Any> = ArrayList(exprPsi ?: emptyList())
    private var isInfixNotation: Boolean = false
    private val partiallyPrintedArguments = ArrayList<ArgumentPrintResult>()
    protected var isDotExpression: Boolean = false

    fun initialize() {
        fun getBlocksInRange (range: TextRange): List<PsiElement> = blocks.filterIsInstance<PsiElement>().filter { range.contains(it.textRange) }
        val replacementMap = HashMap<Pair<PsiElement, PsiElement>, Int>()

        val functionBlock = getOperatorData()
        val argumentsWhichActuallyOccurInText = ArrayList<PT>()
        var firstExplicit : Int? = null

        for (argument in getConcreteParameters()) refactoringContext.rangeData[getExpressionByParameter(argument)]?.let { argRange ->
            if (firstExplicit == null && isExplicit(argument)) firstExplicit = argumentsWhichActuallyOccurInText.size
            replacementMap[getBlocksInRange(argRange).let{ Pair(it.first(), it.last()) }] = argumentsWhichActuallyOccurInText.size
            argumentsWhichActuallyOccurInText.add(argument)
        }
        isDotExpression = replacementMap.contains(Pair(functionBlock, functionBlock)) // Meaningful only for AppExpr
        replacementMap[Pair(functionBlock, functionBlock)] = FUNCTION_INDEX

        for (e in replacementMap) {
            val sI = blocks.indexOf(e.key.first)
            val eI = blocks.indexOf(e.key.second)
            if (sI == -1 || eI == -1)
                throw IllegalArgumentException()
            blocks.removeAll(blocks.subList(sI, eI + 1).toSet())
            blocks.add(sI, e.value)
        }

        val spacingMap = HashMap<Int, String>()
        var buffer: String? = null
        for (block in blocks) when (block) {
            is Int -> {
                val correctedIndex = if (block == -1 && firstExplicit != null) firstExplicit!! else block // needed for correct operation in binary expressions
                if (buffer != null) spacingMap[correctedIndex] = buffer
                buffer = ""
            }
            is PsiElement -> {
                buffer += block.text
            }
        }

        for ((index, argument) in argumentsWhichActuallyOccurInText.withIndex())
            partiallyPrintedArguments.add(ArgumentPrintResult(printParameter(argument), isExplicit(argument), spacingMap[index]))

        isInfixNotation = blocks.indexOf(FUNCTION_INDEX) != 0
    }

    abstract fun printParameter(parameter: PT): IntermediatePrintResult
    abstract fun isExplicit(parameter: PT): Boolean
    abstract fun getConcreteParameters(): List<PT>
    abstract fun getExpressionByParameter(parameter: PT): ET
    abstract fun getOperatorData(): PsiElement
    override fun getArguments(): List<ArgumentPrintResult> = partiallyPrintedArguments
    override fun getParameters(): Pair<List<Parameter>, List<NewParameter>> = Pair(refactoringContext.descriptor.oldParametersConstructor!!, refactoringContext.descriptor.newParametersConstructor!!)
    override fun getContextName(): String = contextSpecificName
    protected fun getBlocksInRange (range: TextRange): List<PsiElement> = blocks.filterIsInstance<PsiElement>().filter { range.contains(it.textRange) }
}

open class AppExpressionEntry(private val concreteAppExpr: Concrete.AppExpression, private val psiAppExpr: ArendArgumentAppExpr, contextSpecificName: String, refactoringContext: ChangeSignatureRefactoringContext):
    BinOpEntry<Concrete.Expression, Concrete.Argument>(concreteAppExpr, psiAppExpr, contextSpecificName, refactoringContext) {
    override fun printParameter(parameter: Concrete.Argument): IntermediatePrintResult = printAppExpr(parameter.expression, psiAppExpr, contextSpecificName, refactoringContext)
    override fun isExplicit(parameter: Concrete.Argument): Boolean = parameter.isExplicit
    override fun getConcreteParameters(): List<Concrete.Argument> = concreteAppExpr.arguments
    override fun getOperatorData(): PsiElement = getBlocksInRange(refactoringContext.rangeData[concreteAppExpr.function]!!).let{ if (it.size != 1) throw IllegalArgumentException() else it.first() }
    override fun getExpressionByParameter(parameter: Concrete.Argument): Concrete.Expression = parameter.expression
    override fun getParameters(): Pair<List<Parameter>, List<NewParameter>> =
        Pair(refactoringContext.descriptor.oldParameters, refactoringContext.descriptor.newParameters.let { if (isDotExpression) it.drop(1) else it })
    fun procFunction() = refactoringContext.textGetter(concreteAppExpr.function.data as PsiElement)
    override fun getContextName(): String = if (isDotExpression) procFunction() else contextSpecificName //TODO: Fixme (we should remove backticks in certain situations)
}

class PatternEntry(private val concreteConstructorPattern: Concrete.ConstructorPattern, private val psiPattern: ArendPattern, contextSpecificName: String, refactoringContext: ChangeSignatureRefactoringContext):
    BinOpEntry<Concrete.Pattern, Concrete.Pattern>(concreteConstructorPattern, psiPattern, contextSpecificName, refactoringContext) {
    override fun printParameter(parameter: Concrete.Pattern): IntermediatePrintResult = printPattern(parameter, psiPattern, contextSpecificName, refactoringContext)
    override fun isExplicit(parameter: Concrete.Pattern): Boolean = parameter.isExplicit
    override fun getConcreteParameters(): List<Concrete.Pattern> = concreteConstructorPattern.patterns
    override fun getExpressionByParameter(parameter: Concrete.Pattern): Concrete.Pattern = parameter
    override fun getOperatorData(): PsiElement = concreteConstructorPattern.constructorData as ArendPattern
}

class LocalCoClauseEntry(private val psiLocalCoClause: ArendLocalCoClause, refactoringContext: ChangeSignatureRefactoringContext, private val contextSpecificName: String): UsageEntry(refactoringContext, psiLocalCoClause) {
    private val procArguments = ArrayList<ArgumentPrintResult>()
    init {
        val spacingMap = HashMap<ArendLamParam, String>()
        var buffer = ""
        for (block in psiLocalCoClause.childrenWithLeaves) {
            if (block is ArendLamParam) {
                spacingMap[block] = buffer
                buffer = ""
            } else if (!psiLocalCoClause.children.contains(block) &&
                block.elementType != PIPE &&
                block.prevSibling?.let{ it.elementType == PIPE} != true) buffer += block.text
        }

        for (arg in psiLocalCoClause.lamParamList) {
            val data = arg.data as PsiElement
            var data1: PsiElement? = data
            while (true) {
                if (data1 is ArendNameTele && data1.identifierOrUnknownList.size == 1 && data1.type == null) {
                    data1 = data1.identifierOrUnknownList[0]; continue
                }
                break
            }
            val text = refactoringContext.textGetter(data)
            val strippedText = if (data1 != null) refactoringContext.textGetter(data1) else null
            val isExplicit = when (data) {
                is ArendNameTele -> data.isExplicit
                is ArendPattern -> data.isExplicit
                else -> data.text.trim().startsWith("{")
            }
            procArguments.add(ArgumentPrintResult(IntermediatePrintResult(text, strippedText, null, data1 is ArendIdentifierOrUnknown, null), isExplicit, spacingMap[arg]))
        }
    }
    override fun getLambdaParams(parameterMap: Set<Parameter>, includingSuperfluousTrailingParams: Boolean): List<Parameter> = emptyList()

    override fun getArguments(): List<ArgumentPrintResult> = procArguments

    override fun getParameters(): Pair<List<Parameter>, List<NewParameter>> = Pair(refactoringContext.descriptor.oldParameters, refactoringContext.descriptor.newParameters)

    override fun getUnmodifiableSuffix(): String? {
        val children = psiLocalCoClause.childrenWithLeaves.toList()
        val index = psiLocalCoClause.fatArrow?.let { children.indexOf(it) } ?: -1
        return if (index != -1) buildString {
          for (c in children.subList(index - 1, children.size)) append(refactoringContext.textGetter(c))
        } else null
    }

    override fun getUnmodifiablePrefix(): String = buildString {
        val children = psiLocalCoClause.childrenWithLeaves.toList()
        val index = psiLocalCoClause.longName?.let{ children.indexOf(it) } ?: 1
        for (c in children.subList(0, index)) append(refactoringContext.textGetter(c))
    }

    override fun getContextName(): String = contextSpecificName
}

class NoArgumentsEntry(refExpr: Concrete.ReferenceExpression, refactoringContext: ChangeSignatureRefactoringContext, private val definitionName: String): UsageEntry(refactoringContext, refExpr.data as ArendCompositeElement) {
    override fun getArguments(): List<ArgumentPrintResult> = emptyList()

    override fun getParameters(): Pair<List<Parameter>, List<NewParameter>> = Pair(refactoringContext.descriptor.oldParameters, refactoringContext.descriptor.newParameters)

    override fun getContextName(): String = definitionName
}

private enum class RenderedParameterKind {INFIX_LEFT, INFIX_RIGHT}

fun printUsageEntry(entry: UsageEntry, globalReferable: GlobalReferable? = null) :
        Pair<String /* default version */, String? /* version with parenthesized operator; makes sense only for AppExpr */ > {
    val defaultBuilder = StringBuilder()
    val parenthesizedPrefixBuilder = StringBuilder()
    fun append(text: String) { defaultBuilder.append(text); parenthesizedPrefixBuilder.append(text) }

    val (oldParameters, newParameters) = entry.getParameters()

    entry.getUnmodifiablePrefix()?.let { defaultBuilder.append(it); parenthesizedPrefixBuilder.append(it) }

    var i = 0
    var j = 0
    val parameterMap = HashMap<Parameter, ArgumentPrintResult?>()
    while (i < oldParameters.size && j < entry.getArguments().size) {
        val param = oldParameters[i]
        val arg = entry.getArguments()[j]
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

    val context = entry.contextPsi.scope.elements.map { VariableImpl(it.textRepresentation()) }
    val referables = ArrayList<Variable>()
    val oldArgToLambdaArgMap = HashMap<Parameter, String>()
    var lambdaArgs = ""
    val lambdaParams = entry.getLambdaParams(parameterMap.keys, false)

    lambdaParams.map {
        val freshName = StringRenamer().generateFreshName(VariableImpl(it.referable?.refName), context + referables)
        referables.add(VariableImpl(freshName))
        lambdaArgs += if (it.isExplicit) " $freshName" else " {$freshName}"
        oldArgToLambdaArgMap[it] = freshName
    }

    val infixNotationSupported = newParameters.withIndex().filter { it.value.isExplicit }.let { explicitParams -> explicitParams.size == 2 && explicitParams.all { it.index >= newParameters.size - 2 } } &&
            globalReferable?.precedence?.isInfix == true

    fun renderParameter(oldParam: Parameter?, isExplicit: Boolean, parameterInfo: RenderedParameterKind? = null): Pair<String /* text */, String /* spacing */> {
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
            (parameter == null) -> Pair("_", false)
            else -> Pair(parameter.printResult.strippedText ?: parameter.printResult.text, !parameter.printResult.isAtomic && !inhibitParens)
        }

        val result = if (isExplicit) (if (requiresParentheses) "(${text})" else text) else (if (text.startsWith("-")) "{ ${text}}" else "{${text}}")
        val spacingText = parameter?.spacingText ?: " "
        return Pair(result, spacingText)
    }

    fun printParams(params: List<NewParameter>) {
        var implicitArgPrefix = ""
        var spacingContents = ""
        for (newParam in params) {
            val oldParam = newParam.oldParameter
            if (!lambdaParams.contains(oldParam) && entry.getLambdaParams(parameterMap.keys, true).contains(oldParam)) break

            val (text, spacing) = renderParameter(oldParam, newParam.isExplicit)

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


    if (infixNotationSupported && (entry is AppExpressionEntry || entry is PatternEntry) && lambdaArgs == "") {
        val (leftParam, rightParam) = newParameters.filter { it.isExplicit }.toList().let { Pair(it[0], it[1]) }
        val (leftText, leftSpacing) = renderParameter(leftParam.oldParameter, isExplicit = true, RenderedParameterKind.INFIX_LEFT)
        val (rightText, rightSpacing) = renderParameter(rightParam.oldParameter, isExplicit = true, RenderedParameterKind.INFIX_RIGHT)

        val contextName = entry.getContextName()  //TODO: Fixme; we should take backticks into account
        append("$leftText$leftSpacing$contextName")

        printParams(newParameters.filter { !it.isExplicit })
        append("$rightSpacing$rightText")
    } else {
        val defClassMode = entry.refactoringContext.descriptor.affectedDefinition is ArendDefClass

        if (lambdaArgs != "" && !defClassMode && entry !is PatternEntry) append("\\lam$lambdaArgs => ")
        for (e in oldArgToLambdaArgMap) parameterMap[e.key] =
            ArgumentPrintResult(IntermediatePrintResult(if (entry is PatternEntry) "_" else e.value, null, null, true, null), true, null)

        val contextName = entry.getContextName()
        defaultBuilder.append(contextName); parenthesizedPrefixBuilder.append(if (globalReferable?.precedence?.isInfix == true) "($contextName)" else contextName)

        printParams(newParameters.filter { !defClassMode || !oldArgToLambdaArgMap.keys.contains(it.oldParameter) })

        while (j < entry.getArguments().size) {
            append(" ${entry.getArguments()[j].printResult.text}")
            j++
        }
    }

    entry.getUnmodifiableSuffix()?.let {
        append(it)
    }
    return Pair(defaultBuilder.toString(), if (lambdaArgs == "") parenthesizedPrefixBuilder.toString() else null)
}
fun printAppExpr(expr: Concrete.Expression, psiElement: ArendArgumentAppExpr, contextSpecificName: String, refactoringContext: ChangeSignatureRefactoringContext): IntermediatePrintResult {
    if (expr is Concrete.AppExpression) {
        val resolve = tryResolveFunctionName(expr.function.data as PsiElement)
        val resolveIsInfix = resolve is GlobalReferable && resolve.precedence.isInfix
        val appExprEntry = AppExpressionEntry(expr, psiElement, contextSpecificName, refactoringContext); appExprEntry.initialize()

        return if (refactoringContext.descriptor.affectedDefinition == resolve) {
            val processResult = printUsageEntry(appExprEntry, resolve as? GlobalReferable)
            val isLambda = processResult.second == null
            IntermediatePrintResult(if (isLambda) "(${processResult.first})" else processResult.first, if (isLambda) processResult.first else null, processResult.second,false, resolve as? GlobalReferable)
        } else {
            val builder = StringBuilder()
            val parenthesizedBuilder = StringBuilder()
            fun append(text: String) { builder.append(text); parenthesizedBuilder.append(text) }

            val processedFunction = appExprEntry.procFunction()
            var explicitArgCount = 0
            val isAtomic = if (appExprEntry.blocks.size == 1 && appExprEntry.blocks[0] == FUNCTION_INDEX) getStrippedPsi(expr.function.data as PsiElement).second else false

            for (block in appExprEntry.blocks) when (block) {
                is Int -> if (block == FUNCTION_INDEX) append(processedFunction) else appExprEntry.getArguments()[block].let {
                    if (it.isExplicit) explicitArgCount++
                    val textInBrackets = "{${it.printResult.strippedText ?: it.printResult.text}}"
                    val text = if (it.isExplicit) (if (explicitArgCount == 2 && resolveIsInfix &&
                        it.printResult.parenthesizedPrefixText != null) it.printResult.parenthesizedPrefixText else it.printResult.text) else textInBrackets
                    val parenthesizedText = if (it.isExplicit) (if (resolveIsInfix && it.printResult.parenthesizedPrefixText != null) it.printResult.parenthesizedPrefixText else it.printResult.text) else textInBrackets
                    builder.append(text); parenthesizedBuilder.append(parenthesizedText)
                }
                is PsiElement -> append(refactoringContext.textGetter(block))
            }
            IntermediatePrintResult(builder.toString().let { if (isAtomic && resolveIsInfix) "($it)" else it}, null, parenthesizedBuilder.toString(), isAtomic, resolve as? GlobalReferable)
        }
    } else if (expr is Concrete.LamExpression && expr.data == null) {
        val builder = StringBuilder()
        val exprBody = expr.body
        if (exprBody is Concrete.AppExpression) {
            val function = exprBody.function.data as PsiElement
            val resolve = tryResolveFunctionName(function)
            val isPostfix = (function as ArendIPName).postfix != null
            if (isPostfix && refactoringContext.descriptor.affectedDefinition == resolve) {
                val entry = object: AppExpressionEntry(exprBody, psiElement, contextSpecificName, refactoringContext) {
                    override fun getLambdaParams(parameterMap: Set<Parameter>, includingSuperfluousTrailingParams: Boolean): List<Parameter> =
                        (this.getParameters().first.firstOrNull { it.isExplicit }?.let { listOf(it) } ?: emptyList()) + super.getLambdaParams(parameterMap, includingSuperfluousTrailingParams)

                    override fun getArguments(): List<ArgumentPrintResult> =
                        listOf(ArgumentPrintResult(IntermediatePrintResult("", null,  null, true, null), isExplicit = true, spacingText = null)) + super.getArguments()
                }
                entry.initialize()
                builder.append(printUsageEntry(entry).first)
                return IntermediatePrintResult(builder.toString(), null, null, false, null)
            }
        }
        builder.append(printAppExpr(exprBody, psiElement, contextSpecificName, refactoringContext).text)
        return IntermediatePrintResult(builder.toString(), null, null,false, null)
    } else if (expr is Concrete.ReferenceExpression) {
        if (expr.referent == refactoringContext.descriptor.affectedDefinition) {
            val text = printUsageEntry(NoArgumentsEntry(expr, refactoringContext, contextSpecificName)).first
            return IntermediatePrintResult("(${text})", text, null, true, null)
        } else if ((expr.referent as? GlobalReferable)?.precedence?.isInfix == true) {
            val text = refactoringContext.textGetter(expr.data as PsiElement)
            return IntermediatePrintResult("(${text})", null, null, true, null)
        }
    }

    val (exprData, isAtomic) = getStrippedPsi(expr.data as PsiElement)
    return IntermediatePrintResult(refactoringContext.textGetter(expr.data as PsiElement), refactoringContext.textGetter(exprData), null, isAtomic, null)
}

fun printPattern(pattern: Concrete.Pattern, psiElement: ArendPattern, contextSpecificName: String, refactoringContext: ChangeSignatureRefactoringContext): IntermediatePrintResult {
    val isInsideAppExprLeaf = !(pattern.data == psiElement ||
            (pattern.data as? ArendPattern)?.let{ it.constructorReference != null && it.parent == psiElement } == true)

    if (pattern is Concrete.ConstructorPattern && !isInsideAppExprLeaf) {
        val constructor = (pattern.constructor as DataLocatedReferable).data?.element
        val constructorIsInfix = constructor is GlobalReferable && constructor.precedence.isInfix
        val patternEntry = PatternEntry(pattern, psiElement, contextSpecificName, refactoringContext); patternEntry.initialize()
        val asTextWithWhitespace = (psiElement.asPattern?.getWhitespace(SpaceDirection.LeadingSpace) ?: "") + (psiElement.asPattern?.text ?: "")
        return if (refactoringContext.descriptor.affectedDefinition == constructor) {
            val processResult = printUsageEntry(patternEntry, constructor as? GlobalReferable)
            val baseText = processResult.first + asTextWithWhitespace
            val parenthesizedText = if (!psiElement.isExplicit) "{$baseText}" else baseText
            val strippedText = if (!psiElement.isExplicit) baseText else null
            IntermediatePrintResult(parenthesizedText, strippedText, processResult.second,false, constructor as? GlobalReferable)
        } else {
            val builder = StringBuilder()
            var explicitArgCount = 0
            val processedFunction = refactoringContext.textGetter(pattern.constructorData as ArendPattern)
            val (strippedFunc, isAtomic) = if (patternEntry.blocks.size == 1 && patternEntry.blocks[0] == FUNCTION_INDEX) getStrippedPsi(pattern.data as PsiElement) else Pair(null, false)
            val strippedText = strippedFunc?.let { refactoringContext.textGetter(it) }

            for (block in patternEntry.blocks) when (block) {
                is Int -> if (block == FUNCTION_INDEX) builder.append(processedFunction) else patternEntry.getArguments()[block].let {
                    if (it.isExplicit) explicitArgCount++
                    val textInBrackets = "{${it.printResult.strippedText ?: it.printResult.text}}"
                    val text = if (it.isExplicit) (if (explicitArgCount == 2 && constructorIsInfix && it.printResult.parenthesizedPrefixText != null) it.printResult.parenthesizedPrefixText else it.printResult.text) else textInBrackets
                    builder.append(text)
                }
                is PsiElement -> builder.append(refactoringContext.textGetter(block))
            }
            IntermediatePrintResult(builder.toString().let { if (isAtomic && constructorIsInfix) "($it)" else it},
                strippedText, null, isAtomic, constructor as? GlobalReferable)
        }
    }

    fun textGetterForPatterns(exprData: PsiElement, preferStrippedVersion: Boolean): String {
        val replacementTextEntry = refactoringContext.textReplacements[exprData]
        if (replacementTextEntry != null) return when (preferStrippedVersion) {
            true ->  replacementTextEntry.second ?: replacementTextEntry.first
            false -> replacementTextEntry.first
        }
        if (exprData.firstChild == null) return exprData.text
        val childRange = if (exprData is ArendPattern && !exprData.isExplicit && preferStrippedVersion) {
            val lbrace = exprData.childrenWithLeaves.indexOfFirst { it.elementType == LBRACE }
            val rbrace = exprData.childrenWithLeaves.indexOfFirst { it.elementType == RBRACE }
            if (lbrace == -1 || rbrace == -1 || lbrace - 1 > rbrace)
                exprData.childrenWithLeaves.toList() else
                exprData.childrenWithLeaves.toList().subList(lbrace + 1, rbrace)
        } else exprData.childrenWithLeaves.toList()

        return buildString {
            for (c in childRange)
                append(textGetterForPatterns(c, false))
        }
    }

    val (exprData, isAtomic) = getStrippedPsi(pattern.data as PsiElement)
    return IntermediatePrintResult(textGetterForPatterns(exprData, true), null, null, isAtomic, null)
}

class ChangeArgumentExplicitnessIntention : SelfTargetingIntention<ArendCompositeElement>(ArendCompositeElement::class.java, ArendBundle.message("arend.coClause.changeArgumentExplicitness")) {
    override fun isApplicableTo(element: ArendCompositeElement, caretOffset: Int, editor: Editor): Boolean {
        if (DumbService.isDumb(element.project)) return false

        return when (element) {
            is ArendNameTele, is ArendFieldTele, is ArendTypeTele ->
                element.parent?.let{ it is ArendDefinition<*> || it is ArendClassField || it is ArendConstructor } ?: false
            else -> false
        }
    }

    override fun applyTo(element: ArendCompositeElement, project: Project, editor: Editor) {
        val elementOnCaret = element.containingFile.findElementAt(editor.caretModel.offset)
        val switchedArgIndexInTele = getSwitchedArgIndex(element, elementOnCaret)
        val def = element.ancestor() as? PsiReferable ?: return
        val teleIndexInDef = getTeleIndexInDef(def, element)

        val refactoringDescriptors = HashSet<ChangeSignatureRefactoringDescriptor>()
        if (def is ArendDefClass) {
            val descendants = ClassDescendantsSearch(project).getAllDescendants(def)
            for (clazz in descendants.filterIsInstance<ArendDefClass>().union(singletonList(def))) {
                val fieldDefIdentifiers = ClassFieldImplScope(clazz, false).elements
                val oldParameters = fieldDefIdentifiers.map { Parameter(if (it is ArendFieldDefIdentifier) it.isExplicitField else true, it) }
                val switchedFieldIdentifiers: List<Referable> = if (switchedArgIndexInTele == null || switchedArgIndexInTele == -1) {
                    (element as ArendFieldTele).referableList
                } else {
                    singletonList((element as ArendFieldTele).referableList[switchedArgIndexInTele])
                }
                refactoringDescriptors.add(ChangeSignatureRefactoringDescriptor(clazz, oldParameters, oldParameters.map { NewParameter(if (switchedFieldIdentifiers.contains(it.referable)) !it.isExplicit else it.isExplicit, it) }))
            }

            NameFieldApplier(project).applyTo(refactoringDescriptors)
        } else {
            val indices = if (switchedArgIndexInTele == null || switchedArgIndexInTele == -1) {
                val teleSize = if (element is ArendTypeTele && element.typedExpr?.identifierOrUnknownList?.isEmpty() == true) 1 else getTele(element)?.size ?: return
                (0 until teleSize).map { it + teleIndexInDef }
            } else {
                singletonList(switchedArgIndexInTele + teleIndexInDef)
            }

            if (def is ArendFunctionDefinition<*>) {
                val params = ArendChangeInfo.getParameterInfo(def)
                val newParams = params.map { ArendParameterInfo(it.name, it.typeText, it.oldIndex, if (indices.contains(it.oldIndex)) !it.isExplicit() else it.isExplicit()) }.toList()
                runWriteAction {
                    fixElim(def, ArendChangeInfo(newParams, "", "", def))
                }
            }

            chooseApplier(element)?.applyTo(def, indices.toSet())
        }

        runWriteAction {
            if (switchedArgIndexInTele == null || switchedArgIndexInTele == -1) {
                switchTeleExplicitness(element)
            } else {
                chooseApplier(element)?.rewriteDef(element, switchedArgIndexInTele)
            }
        }
    }

    override fun startInWriteAction(): Boolean = false

    private fun chooseApplier(element: ArendCompositeElement): ChangeArgumentExplicitnessApplier? {
        return when (element) {
            is ArendNameTele, is ArendFieldTele -> NameFieldApplier(element.project)
            is ArendTypeTele -> if (element.parent is ArendConstructor || element.parent is ArendDefData) NameFieldApplier(element.project) else TypeApplier(element.project)
            else -> null
        }
    }

    private fun getSwitchedArgIndex(tele: ArendCompositeElement, switchedArg: PsiElement?): Int? {
        switchedArg ?: return -1
        if (tele is ArendTypeTele && getTele(tele) == null) return 0
        val argsText = getTele(tele)?.map { it.text }
        return if (argsText?.size == 1) 0 else argsText?.indexOf(switchedArg.text)
    }

    private fun switchTeleExplicitness(tele: ArendCompositeElement) {
        val def = tele.ancestor<PsiReferable>() as PsiElement
        val teleIndex = def.children.indexOf(tele)
        val anchor = def.children[teleIndex - 1]
        val factory = ArendPsiFactory(tele.project)
        val newTele = createSwitchedTele(factory, tele) ?: return
        def.children[teleIndex].delete()
        val inserted = def.addAfter(newTele, anchor)
        val ws = factory.createWhitespace(" ")
        def.addBefore(ws, inserted)
    }
}

abstract class ChangeArgumentExplicitnessApplier(val project: Project) {
    protected val factory = ArendPsiFactory(project)

    fun applyTo(def: PsiReferable, indexInDef: Set<Int>) {
        val oldParameters = getAllParametersForReferable(def).map { p -> p.referableList.map { Parameter(p.isExplicit, it) } }.flatten() // PsiElementUtils.getTelesFromDef is similar to this
        val oldParametersConstructor = if (def is ArendConstructor) getAllParametersForReferable(def, false).map { p -> p.referableList.map { Parameter(p.isExplicit, it) } }.flatten() else null
        val numberOfDataArgs = if (oldParametersConstructor != null) oldParameters.size - oldParametersConstructor.size else 0
        val newParameters = oldParameters.withIndex().map { NewParameter(
            if (indexInDef.contains(it.index)) !it.value.isExplicit else it.value.isExplicit,
            it.value
        ) } // Might be generalized in the future
        val newParametersConstructor = oldParametersConstructor?.withIndex()?.map { NewParameter(
            if (indexInDef.contains(it.index + numberOfDataArgs)) !it.value.isExplicit else it.value.isExplicit,
            it.value
        ) }

        val descriptor = ChangeSignatureRefactoringDescriptor(def, oldParameters, newParameters, oldParametersConstructor, newParametersConstructor)
        applyTo(singletonList(descriptor).toSet())
    }

    fun applyTo(referableData: Set<ChangeSignatureRefactoringDescriptor>) {
        val rootPsiEntries: LinkedHashSet<Pair<PsiElement, ChangeSignatureRefactoringDescriptor>> = referableData.map { descriptor ->
            ReferencesSearch.search(descriptor.affectedDefinition).map {
                Pair(it.element, descriptor)
            }.filter {
                when (val psi = it.first) {
                    is ArendRefIdentifier -> (psi.parent as? ArendLongName)?.let { longName -> longName.refIdentifierList.lastOrNull() == psi } ?: true
                    else -> true
                }
        }.map { Pair(getParentPsiFunctionCall(it.first), it.second )} }.flatten().sortedWith { a, b ->
            if (a.first.textLength == b.first.textLength) b.first.startOffset.compareTo(a.first.startOffset) else a.first.textLength.compareTo(b.first.textLength)
        }.toCollection(LinkedHashSet())

        val concreteSet = LinkedHashSet<ConcreteDataItem>()
        val textReplacements = LinkedHashMap<PsiElement, Pair<String /* Possibly parenthesized */, String? /* Not Parenthesized */>>()
        val fileChangeMap = LinkedHashMap<PsiFile, SortedList<Pair<TextRange, Pair<String, String?>>>>()
        val rangeData = HashMap<Concrete.SourceNode, TextRange>()
        val refactoringTitle = ArendBundle.message("arend.coClause.changeArgumentExplicitness")
        val rootPsiWithErrors = HashSet<Pair<PsiElement, PsiReferable>>()

        if (!ProgressManager.getInstance().runProcessWithProgressSynchronously({
            runReadAction {
                val progressIndicator = ProgressManager.getInstance().progressIndicator
                for ((index, rootPsiEntry) in rootPsiEntries.withIndex()) {
                    val (rootPsi, refactoringDescriptor) = rootPsiEntry
                    progressIndicator.fraction = index.toDouble() / rootPsiEntries.size
                    progressIndicator.checkCanceled()
                    val errorReporter = CountingErrorReporter(GeneralError.Level.ERROR, DummyErrorReporter.INSTANCE)
                    when (rootPsi) {
                        is ArendArgumentAppExpr -> appExprToConcrete(rootPsi, false, errorReporter)?.let{
                            concreteSet.add(ConcreteDataItem(rootPsi, refactoringDescriptor, it))
                        }
                        is ArendPattern -> patternToConcrete(rootPsi, errorReporter)?.let {
                            concreteSet.add(ConcreteDataItem(rootPsi, refactoringDescriptor, it))
                        }
                        is ArendLocalCoClause -> concreteSet.add(ConcreteDataItem(rootPsi, refactoringDescriptor, null))
                    }
                    if (errorReporter.errorsNumber > 0) rootPsiWithErrors.add(Pair(rootPsi, refactoringDescriptor.affectedDefinition))
                }

                for ((index, callEntry) in concreteSet.withIndex()) {
                    progressIndicator.fraction = index.toDouble() / concreteSet.size
                    progressIndicator.checkCanceled()
                    val refactoringContext = ChangeSignatureRefactoringContext(callEntry.refactoringDescriptor, textReplacements, rangeData)
                    val contextSpecificDefName = ResolveReferenceAction.getTargetName(callEntry.refactoringDescriptor.affectedDefinition as PsiLocatedReferable, callEntry.psi as ArendCompositeElement)!!
                    rangeData.clear()

                    try {
                        when (callEntry.psi) {
                            is ArendArgumentAppExpr -> {
                                val expr = callEntry.concreteExpr as Concrete.Expression
                                getBounds(expr, callEntry.psi.node.getChildren(null).toList(), rangeData)
                                val printResult = printAppExpr(expr, callEntry.psi, contextSpecificDefName, refactoringContext)
                                Pair(printResult.strippedText ?: printResult.text, null)
                            }
                            is ArendPattern -> {
                                val concretePattern = callEntry.concreteExpr as Concrete.ConstructorPattern
                                getBounds(concretePattern, callEntry.psi.node.getChildren(null).toList(), rangeData)
                                val printResult = printPattern(concretePattern, callEntry.psi, contextSpecificDefName, refactoringContext)
                                Pair(printResult.text, printResult.strippedText)
                            }
                            is ArendLocalCoClause ->
                                Pair(printUsageEntry(LocalCoClauseEntry(callEntry.psi, refactoringContext, contextSpecificDefName)).first, null)
                            else -> null
                        }?.let { result ->
                            textReplacements[callEntry.psi] = result
                        }
                    } catch (e: IllegalArgumentException) {
                        rootPsiWithErrors.add(Pair(callEntry.psi, callEntry.refactoringDescriptor.affectedDefinition))
                    }
                }

                for (replacementEntry in textReplacements) {
                    val file = replacementEntry.key.containingFile
                    val comparator = Comparator<Pair<TextRange, Pair<String, String?>>> { o1, o2 ->
                        val i = o1.first.startOffset - o2.first.startOffset
                        if (i > 0) 1 else if (i < 0) -1 else 0
                    }
                    val changesList = fileChangeMap.computeIfAbsent(file) {
                        SortedList<Pair<TextRange, Pair<String, String?>>>(comparator)
                    }
                    changesList.add(Pair(replacementEntry.key.textRange, replacementEntry.value))
                }

                for (changeEntry in fileChangeMap) { // Leave only non-overlapping top-level text changes
                    var sampleEntry: Pair<TextRange, Pair<String, String?>>? = null
                    var index = 0
                    val list = changeEntry.value
                    while (index < list.size) {
                        val nextEntry = list[index]
                        if (sampleEntry == null || sampleEntry.first.endOffset <= nextEntry.first.startOffset) {
                            sampleEntry = nextEntry
                        } else if (sampleEntry.first.contains(nextEntry.first)) {
                            list.remove(nextEntry)
                            continue
                        } else if (nextEntry.first.contains(sampleEntry.first)) {
                            list.remove(sampleEntry)
                            sampleEntry = nextEntry
                            continue
                        } else throw IllegalStateException() // Changes should not overlap!
                        index++
                    }
                }

                // Assert text changes do not overlap
                for (entry in fileChangeMap) for (i in 1 until entry.value.size) {
                    val precedingRange = entry.value[i-1].first
                    val currentRange = entry.value[i].first
                    assert (precedingRange.endOffset <= currentRange.startOffset)
                }
            }
        }, refactoringTitle, true, project)) return

        if (rootPsiWithErrors.size > 0 && Messages.showYesNoDialog(
                ArendBundle.message("arend.coClause.changeArgumentExplicitness.question1", rootPsiWithErrors.first().second.name ?: "?"),
                refactoringTitle, Messages.getYesButton(), Messages.getNoButton(), Messages.getQuestionIcon()) == Messages.NO) {
            return
        }

        return runWriteAction {
            val docManager = PsiDocumentManager.getInstance(project)

            for (changeEntry in fileChangeMap) {
                val textFile = docManager.getDocument(changeEntry.key)
                if (textFile != null) {
                    docManager.doPostponedOperationsAndUnblockDocument(textFile)
                    for (replacementEntry in changeEntry.value.reversed()) {
                        val textRange = replacementEntry.first
                        textFile.replaceString(textRange.startOffset, textRange.endOffset, replacementEntry.second.first)
                    }
                    docManager.commitDocument(textFile)
                }
            }

        }
    }

    /**
     * @param tele  telescope which explicitness was changed
     * @param indexInTele  argument index among all arguments
     * @return  new telescope in definition
     */
    fun rewriteDef(tele: ArendCompositeElement, indexInTele: Int): PsiElement {
        val teleSize = getTele(tele)?.size
        if (teleSize != null && teleSize > 1) {
            splitTele(tele, indexInTele)
        }

        val newTele = createSwitchedTele(factory, tele)
        newTele ?: return tele

        return tele.replace(newTele)
    }

    /**
     * Returns the closest ancestor psi-call for element
     */
    abstract fun getParentPsiFunctionCall(element: PsiElement): PsiElement

    /**
     * @return  parameters for given call
     */
    abstract fun getCallingParameters(call: PsiElement): List<PsiElement>

    abstract fun getContext(element: PsiElement): List<Variable>

    /**
     * Returns all parameters including phantoms
     */
    abstract fun getCallingParametersWithPhantom(call: PsiElement): List<String>

    /**
     * Creates psi (AtomArgument) from text
     */
    abstract fun createArgument(arg: String): PsiElement
}

class NameFieldApplier(project: Project) : ChangeArgumentExplicitnessApplier(project) {
    override fun getParentPsiFunctionCall(element: PsiElement): PsiElement {
        val pattern = element.parentOfType<ArendPattern>()
        if (pattern != null) return if (pattern.parent is ArendPattern) pattern.parent else pattern
        return element.parentOfType<ArendArgumentAppExpr>() ?: element
    }

    override fun getCallingParameters(call: PsiElement): List<PsiElement> = when (call) {
        is ArendArgumentAppExpr -> call.argumentList
        is ArendPattern -> call.sequence
        else -> throw IllegalArgumentException()
    }

    override fun getContext(element: PsiElement): List<Variable> {
        val argumentAppExpr = element as ArendArgumentAppExpr
        return argumentAppExpr.scope.elements.map { VariableImpl(it.textRepresentation()) }
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
            is ArendExpr -> {
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

class TypeApplier(project: Project) : ChangeArgumentExplicitnessApplier(project) {
    override fun getParentPsiFunctionCall(element: PsiElement): PsiElement =
        element.ancestor<ArendLocalCoClause>() ?: element

    override fun getCallingParameters(call: PsiElement): List<PsiElement> = (call as ArendLocalCoClause).lamParamList

    override fun getContext(element: PsiElement): List<Variable> {
        val localCoClause = element as ArendLocalCoClause
        return localCoClause.scope.elements.map { VariableImpl(it.textRepresentation()) }
    }

    override fun getCallingParametersWithPhantom(call: PsiElement): List<String> {
        return getCallingParameters(call).map { it.text }
    }

    override fun createArgument(arg: String): PsiElement = factory.createCoClause("dummy $arg").lamParamList.first()
}

/**
 * Creates telescope with a changed parens
 */
private fun createSwitchedTele(factory: ArendPsiFactory, tele: ArendCompositeElement): ArendCompositeElement? {
    val params = getTele(tele)?.joinToString(" ") { it.text }

    return when (tele) {
        is ArendNameTele -> {
            val isExplicit = tele.isExplicit
            val type = tele.type!!.text
            factory.createNameTele(params, type, !isExplicit)
        }

        is ArendFieldTele -> {
            val isExplicit = tele.isExplicit
            val type = tele.type!!.text
            factory.createFieldTele(params, type, !isExplicit)
        }

        is ArendTypeTele -> {
            val isExplicit = tele.isExplicit
            val typedExpr = tele.typedExpr
            val expr = typedExpr?.type
            if (expr == null) {
                if (typedExpr != null)
                    factory.createTypeTele(null, typedExpr.text, !isExplicit) else
                        factory.createTypeTele("", tele.text, !isExplicit)
            } else {
                factory.createTypeTele(params, expr.text, !isExplicit)
            }
        }
        else -> null
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

private fun ArendPsiFactory.createArgumentAppExpr(expr: String): ArendArgumentAppExpr =
    createExpression(expr).childOfType() ?: error("Failed to create argument app expr: `$expr`")

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
