package org.arend.refactoring.changeSignature.entries

import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.arend.codeInsight.ParameterDescriptor
import org.arend.codeInsight.SignatureUsageContext
import org.arend.psi.ArendElementTypes
import org.arend.psi.childrenWithLeaves
import org.arend.psi.ext.*
import org.arend.refactoring.changeSignature.*
import java.util.HashMap

class LocalCoClauseEntry(private val psiLocalCoClause: ArendLocalCoClause,
                         refactoringContext: ChangeSignatureRefactoringContext,
                         private val descriptor1: ChangeSignatureRefactoringDescriptor
): UsageEntry(refactoringContext, psiLocalCoClause, descriptor1, null) {
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
            val text = refactoringContext.textGetter(data)
            val strippedText = if (data1 != null) refactoringContext.textGetter(data1) else null
            val isExplicit = when (data) {
                is ArendNameTele -> data.isExplicit
                is ArendPattern -> data.isExplicit
                else -> !data.text.trim().startsWith("{")
            }
            procArguments.add(
                ArgumentPrintResult(
                    IntermediatePrintResult(
                        text,
                        strippedText,
                        null,
                        data1 is ArendIdentifierOrUnknown,
                        null
                    ), isExplicit, spacingMap[arg]
                )
            )
        }
    }
    override fun getLambdaParams(parameterMap: Set<ParameterDescriptor>, includingSuperfluousTrailingParams: Boolean): List<ParameterDescriptor> = emptyList()

    override fun getArguments(): List<ArgumentPrintResult> = procArguments

    override fun getParameters(): Pair<List<ParameterDescriptor>, List<ParameterDescriptor>> {
        val context = SignatureUsageContext.getParameterContext(psiLocalCoClause.lamParamList.firstOrNull() ?: psiLocalCoClause)
        return Pair(context.filterParameters(descriptor1.oldParameters), context.filterParameters(descriptor1.newParameters))
    }

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
}