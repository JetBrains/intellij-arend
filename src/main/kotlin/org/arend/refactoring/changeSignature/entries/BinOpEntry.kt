package org.arend.refactoring.changeSignature.entries

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.arend.naming.reference.Referable
import org.arend.psi.childrenWithLeaves
import org.arend.psi.ext.ArendCompositeElement
import org.arend.refactoring.changeSignature.*
import org.arend.term.concrete.Concrete
import java.util.HashMap

abstract class BinOpEntry<ET : Concrete.SourceNode, PT>(val expr: Concrete.SourceNode,
                                                        contextPsi: ArendCompositeElement,
                                                        refactoringContext: ChangeSignatureRefactoringContext,
                                                        descriptor: ChangeSignatureRefactoringDescriptor?,
                                                        target: Referable?):
    UsageEntry(refactoringContext, contextPsi, descriptor, target) {
    private val exprPsi: List<PsiElement>? = refactoringContext.rangeData[expr]?.let { range ->
        contextPsi.childrenWithLeaves.toList().filter { range.contains(it.textRange) }
    }
    val blocks: ArrayList<Any> = ArrayList(exprPsi ?: emptyList())
    private var isInfixNotation: Boolean = false
    private val partiallyPrintedArguments = ArrayList<ArgumentPrintResult>()
    protected var isDotExpression: Boolean = false

    fun initialize() {
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
            partiallyPrintedArguments.add(
                ArgumentPrintResult(
                    printParameter(argument),
                    isExplicit(argument),
                    spacingMap[index]
                )
            )

        isInfixNotation = blocks.indexOf(FUNCTION_INDEX) != 0
    }

    abstract fun printParameter(parameter: PT): IntermediatePrintResult
    abstract fun isExplicit(parameter: PT): Boolean
    abstract fun getConcreteParameters(): List<PT>
    abstract fun getExpressionByParameter(parameter: PT): ET
    abstract fun getOperatorData(): PsiElement
    override fun getArguments(): List<ArgumentPrintResult> = partiallyPrintedArguments
    protected fun getBlocksInRange (range: TextRange): List<PsiElement> = blocks.filterIsInstance<PsiElement>().filter { range.contains(it.textRange) }
}