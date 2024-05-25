package org.arend.refactoring.changeSignature.entries

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.arend.codeInsight.ParameterDescriptor
import org.arend.ext.module.LongName
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.Referable
import org.arend.psi.ArendPsiFactory
import org.arend.psi.childrenWithLeaves
import org.arend.psi.ext.*
import org.arend.refactoring.changeSignature.*
import org.arend.refactoring.changeSignature.entries.UsageEntry.Companion.RenderedParameterKind
import org.arend.term.concrete.Concrete
import java.util.*
import kotlin.collections.ArrayList

abstract class AbstractExpressionEntry(
    refactoringContext: ChangeSignatureRefactoringContext,
    contextPsi: ArendCompositeElement,
    descriptor: ChangeSignatureRefactoringDescriptor?,
    concreteExpr: Concrete.SourceNode,
    operatorData: PsiElement,
    target: Referable?,
    concreteParameters: List<PrintableArgument>
):
    UsageEntry(refactoringContext, contextPsi, descriptor, target) {
    private val exprPsi: List<PsiElement>? =
        refactoringContext.rangeData[concreteExpr]?.let { range ->
            contextPsi.childrenWithLeaves.toList().filter { range.contains(it.textRange) }
        }
    val blocks: ArrayList<Any> = ArrayList(exprPsi ?: emptyList())
    private var isInfixNotation: Boolean = false
    private val partiallyPrintedArguments = ArrayList<ArgumentPrintResult>()
    private var contextName: String = ""

    override fun getContextName(): String = contextName

    init {
        val replacementMap = HashMap<Pair<PsiElement, PsiElement>, Int>()
        val argumentsWhichActuallyOccurInText = ArrayList<PrintableArgument>()
        var firstExplicit : Int? = null

        for (argument in concreteParameters) refactoringContext.rangeData[argument.getExpression()]?.let { argRange ->
            if (firstExplicit == null && argument.isExplicit()) firstExplicit = argumentsWhichActuallyOccurInText.size
            replacementMap[getBlocksInRange(contextPsi, argRange).let{ Pair(it.first(), it.last()) }] = argumentsWhichActuallyOccurInText.size
            argumentsWhichActuallyOccurInText.add(argument)
        }
        replacementMap[Pair(operatorData, operatorData)] = FUNCTION_INDEX

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
            partiallyPrintedArguments.add(
                ArgumentPrintResult(
                    argument.print(),
                    argument.isExplicit(),
                    spacingMap[index]
                )
            )

        val functionIndex = blocks.indexOf(FUNCTION_INDEX)
        isInfixNotation = functionIndex != 0

        contextName = getContextName(refactoringContext, concreteExpr)

        if (blocks.indexOf(0) == -1 && partiallyPrintedArguments.size >= 1) {
            val thisArgument = partiallyPrintedArguments[0]
            if (!thisArgument.printResult.text.contains(" ") && descriptor == null) {
                blocks.add(functionIndex, 0)
                contextName = "." + target?.refName
            } else {
                blocks.add(functionIndex + 1, ArendPsiFactory(contextPsi.project).createWhitespace(" "))
                blocks.add(functionIndex + 2, 0)
            }
        } else if (partiallyPrintedArguments.size == 1 && isInfixNotation && (target as? ReferableBase<*>)?.getPrecedence()?.isInfix == false && descriptor == null) {
            val longName = LongName.fromString(contextName)
            contextName = LongName(longName.toList().subList(0, longName.size() - 1) + "`${longName.lastName}").toString()
        }
    }

    override fun printUsageEntryInternal(globalReferable: GlobalReferable?,
                                         newParameters: List<ParameterDescriptor>,
                                         parameterMap: MutableMap<ParameterDescriptor, ArgumentPrintResult?>,
                                         hasExplicitExternalArgument: Boolean,
                                         argumentStartIndex: Int,
                                         doubleBuilder: DoubleStringBuilder): Pair<Boolean, Boolean> {
        val lambdaParams = getLambdaParams(parameterMap.keys, false)
        val infixNotationSupported = newParameters.withIndex().filter { it.value.isExplicit }
            .let { explicitParams -> explicitParams.size == 2 && explicitParams.all { it.index >= newParameters.size - 2 } } &&
                globalReferable?.precedence?.isInfix == true

        if (lambdaParams.isEmpty() && infixNotationSupported) {
            val (leftParam, rightParam) = newParameters.filter { it.isExplicit }.toList().let { Pair(it[0], it[1]) }
            val oldLeftParam = leftParam.oldParameter
            val oldRightParam = rightParam.oldParameter
            if (parameterMap[oldLeftParam] != null && parameterMap[oldRightParam] != null) {
                val (leftText, leftSpacing) = printParam(globalReferable, oldLeftParam, leftParam, parameterMap, hasExplicitExternalArgument, RenderedParameterKind.INFIX_LEFT)
                val (rightText, rightSpacing) = printParam(globalReferable, oldRightParam, rightParam, parameterMap, hasExplicitExternalArgument, RenderedParameterKind.INFIX_RIGHT)

                val contextName = getContextName()  //TODO: Fixme; we should take backticks into account
                doubleBuilder.append("$leftText$leftSpacing$contextName")

                printParams(globalReferable,newParameters.filter { !it.isExplicit }, lambdaParams, parameterMap, hasExplicitExternalArgument, doubleBuilder)
                doubleBuilder.append("$rightSpacing$rightText")
                return Pair(false, false)
            }
        }

        return super.printUsageEntryInternal(globalReferable, newParameters, parameterMap, hasExplicitExternalArgument, argumentStartIndex, doubleBuilder)
    }

    override fun getArguments(): List<ArgumentPrintResult> = partiallyPrintedArguments
    
    fun getFunctionName(): String = contextName

    fun isInfix() = isInfixNotation

    companion object {
        fun getBlocksInRange (contextPsi: ArendCompositeElement, range: TextRange): List<PsiElement> =
            contextPsi.childrenWithLeaves.filter { range.contains(it.textRange) }.toList()

        interface PrintableArgument {
            fun print(): IntermediatePrintResult
            fun isExplicit(): Boolean
            fun getExpression(): Concrete.SourceNode
        }

    }
}