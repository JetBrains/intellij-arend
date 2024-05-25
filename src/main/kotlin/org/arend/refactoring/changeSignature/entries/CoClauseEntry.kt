package org.arend.refactoring.changeSignature.entries

import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.arend.codeInsight.ParameterDescriptor
import org.arend.naming.reference.GlobalReferable
import org.arend.psi.ArendElementTypes
import org.arend.psi.childrenWithLeaves
import org.arend.psi.ext.*
import org.arend.refactoring.changeSignature.*
import java.util.HashMap

class CoClauseEntry(private val psiLocalCoClause: CoClauseBase,
                    refactoringContext: ChangeSignatureRefactoringContext,
                    descriptor1: ChangeSignatureRefactoringDescriptor
): UsageEntry(refactoringContext, psiLocalCoClause, descriptor1, psiLocalCoClause.resolvedImplementedField) {
    private val procArguments = ArrayList<ArgumentPrintResult>()
    init {
        val spacingMap = HashMap<ArendLamParam, String>()
        var buffer = ""
        for (block in psiLocalCoClause.childrenWithLeaves) {
            if (block is ArendLamParam) {
                spacingMap[block] = buffer
                buffer = ""
            } else if (!psiLocalCoClause.children.contains(block) &&
                block.elementType != ArendElementTypes.PIPE &&
                block.prevSibling?.let{ it.elementType == ArendElementTypes.PIPE } != true) buffer += block.text
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
            val text = if (data1 != null) refactoringContext.textGetter(data1) else refactoringContext.textGetter(data)
            val isExplicit = when (data) {
                is ArendNameTele -> data.isExplicit
                is ArendPattern -> data.isExplicit
                else -> !data.text.trim().startsWith("{")
            }
            procArguments.add(
                ArgumentPrintResult(
                    IntermediatePrintResult(
                        text,
                        null,
                        data1 is ArendIdentifierOrUnknown,
                        false,
                        null
                    ), isExplicit, spacingMap[arg]
                )
            )
        }
    }

    override fun getLambdaParams(parameterMap: Set<ParameterDescriptor>, includingSuperfluousTrailingParams: Boolean): List<ParameterDescriptor> = emptyList()

    override fun getContextName(): String =
        getCorrectedContextName(refactoringContext,
            psiLocalCoClause.longName?.let { it.refIdentifierList.map { refId -> refId.reference }.zip(it.longName) } ?: emptyList())


    override fun getArguments(): List<ArgumentPrintResult> = procArguments

    override fun printUsageEntryInternal(globalReferable: GlobalReferable?,
                                         newParameters: List<ParameterDescriptor>,
                                         parameterMap: MutableMap<ParameterDescriptor, ArgumentPrintResult?>,
                                         hasExplicitExternalArgument: Boolean,
                                         argumentStartIndex: Int,
                                         doubleBuilder: DoubleStringBuilder): Pair<Boolean, Boolean> {
        val children = psiLocalCoClause.childrenWithLeaves.toList()
        val longNameIndex = psiLocalCoClause.longName?.let{ children.indexOf(it) } ?: 1
        for (c in children.subList(0, longNameIndex))
            doubleBuilder.append(refactoringContext.textGetter(c))

        val result = super.printUsageEntryInternal(globalReferable, newParameters, parameterMap, hasExplicitExternalArgument, argumentStartIndex, doubleBuilder)

        val arrowIndex = psiLocalCoClause.fatArrow?.let { children.indexOf(it) } ?: -1
        if (arrowIndex != -1) {
            for (c in children.subList(arrowIndex - 1, children.size))
                doubleBuilder.append(refactoringContext.textGetter(c))
        }

        return result
    }
}