package org.arend.refactoring.changeSignature

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.util.elementType
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.containers.SortedList
import org.arend.codeInsight.ParameterDescriptor
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.Referable
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.ext.*
import org.arend.refactoring.NsCmdRefactoringAction
import org.arend.refactoring.changeSignature.entries.*
import org.arend.resolving.DataLocatedReferable
import org.arend.term.concrete.Concrete
import java.util.*
import kotlin.collections.ArrayList

fun modifyExternalParameters(oldParameters: List<ParameterDescriptor>,
                             newParameters: List<ParameterDescriptor>,
                             affectedDefinition: PsiReferable,
                             affectedDefinitionOldParameters: List<ParameterDescriptor>): ChangeSignatureRefactoringDescriptor? {
    val relevantParameters = oldParameters.filter { !it.isExternal() && it.getReferable() != null }.map { it.getReferable() }.toSet()
    var relevantSegmentStartIndex = -1
    var relevantSegmentEndIndex = -1
    var externalSegmentEndIndex = -1

    for ((index, parameter) in affectedDefinitionOldParameters.withIndex()) {
        if (parameter.isExternal()) {
            if (relevantParameters.contains(parameter.getReferable())) {
                if (relevantSegmentStartIndex == -1) relevantSegmentStartIndex = index
                continue
            }
        } else {
            if (externalSegmentEndIndex == -1) externalSegmentEndIndex = index
        }

        if (relevantSegmentStartIndex != -1) {
            if (relevantSegmentEndIndex == -1) relevantSegmentEndIndex = index
        }
    }

    if (relevantSegmentEndIndex == -1) {
        if (relevantSegmentStartIndex == -1) return null
        relevantSegmentEndIndex = affectedDefinitionOldParameters.size
    }
    if (externalSegmentEndIndex == -1) {
        externalSegmentEndIndex = affectedDefinitionOldParameters.size
    }

    val relevantReferables = LinkedHashMap<PsiElement, ParameterDescriptor>()

    affectedDefinitionOldParameters.subList(relevantSegmentStartIndex, relevantSegmentEndIndex).forEach {
        relevantReferables[it.getReferable()!!] = it
    }
    val newRelevantSegment = ArrayList<ParameterDescriptor>()
    val internalizedSegment = ArrayList<ParameterDescriptor>()

    for (parameter in newParameters) if (parameter.oldParameter != null) {
        val oldReferable = parameter.oldParameter.getReferable()!!
        if (relevantReferables.keys.contains(oldReferable)) {
            val oldParameter = relevantReferables[oldReferable]!!
            val isData = oldParameter.isDataParameter
            val newParameter = if (isData)
                ParameterDescriptor.createDataParameter(oldParameter, oldParameter.getExternalScope(), parameter.getNameOrNull(), parameter.typeGetter, oldReferable as? PsiReferable) else
                ParameterDescriptor.createNewParameter(false, oldParameter, oldParameter.getExternalScope(), parameter.getNameOrNull(), parameter.typeGetter)
            newRelevantSegment.add(newParameter)
            relevantReferables.remove(oldReferable)
        }
    }

    for (entry in relevantReferables) {
        val isData = entry.value.isDataParameter
        val newParameter = if (isData)
            ParameterDescriptor.createDataParameter(entry.value, null, null, entry.value.typeGetter, entry.value.oldParameter?.getReferable() as? PsiReferable) else
                ParameterDescriptor.createNewParameter(entry.value.isExplicit, entry.value, null, null, entry.value.typeGetter)
        internalizedSegment.add(newParameter)
    }

    val headExternalSegment = affectedDefinitionOldParameters.subList(0, relevantSegmentStartIndex)
    assert (relevantSegmentEndIndex <= externalSegmentEndIndex)
    val tailExternalSegment = affectedDefinitionOldParameters.subList(relevantSegmentEndIndex, externalSegmentEndIndex)
    val internalSegment = affectedDefinitionOldParameters.subList(externalSegmentEndIndex, affectedDefinitionOldParameters.size)

    val definitionNewSignature = ParameterDescriptor.identityTransform(headExternalSegment) + newRelevantSegment + ParameterDescriptor.identityTransform(tailExternalSegment) + internalizedSegment + ParameterDescriptor.identityTransform(internalSegment)
    val descriptor = ChangeSignatureRefactoringDescriptor(affectedDefinition, affectedDefinitionOldParameters, definitionNewSignature, null)

    return if (descriptor.isTrivial()) null else descriptor
}

/**
 * This class encodes the context of usages-modifying functionality of ChangeSignatureRefactoring
*  */
data class ChangeSignatureRefactoringContext(val refactoringDescriptors: List<ChangeSignatureRefactoringDescriptor>,
                                             val textReplacements: HashMap<PsiElement, Pair<String, String?>>,
                                             val rangeData: HashMap<Concrete.SourceNode, TextRange>,
                                             val deferredNsCmds: MutableList<NsCmdRefactoringAction> = ArrayList()) {
    fun textGetter(psi: PsiElement): String =
        textReplacements[psi]?.let { it.second ?: it.first } ?: buildString {
            if (psi.firstChild == null) append(psi.text) else
                for (c in psi.childrenWithLeaves.toList()) append(textGetter(c))
        }

    fun identifyDescriptor(referable: Referable): ChangeSignatureRefactoringDescriptor? = refactoringDescriptors.firstOrNull { it.affectedDefinition == referable }
}

data class ConcreteDataItem(val psi: PsiElement,
                            val concreteExpr: Concrete.SourceNode?)
data class IntermediatePrintResult(val text: String,
                                   val strippedText: String?,
                                   val parenthesizedPrefixText: String?,
                                   val isAtomic: Boolean,
                                   val referable: GlobalReferable?)
data class ArgumentPrintResult(val printResult: IntermediatePrintResult,
                               val isExplicit: Boolean,
                               val spacingText: String?)

const val FUNCTION_INDEX = -1

fun getRefToFunFromLongName(longName: ArendLongName): PsiElement? {
    val ref = longName.children.last() as? ArendRefIdentifier
    return ref?.resolve
}

fun tryResolveFunctionName(element: PsiElement): PsiElement? =
    if (element is ArendLongName) {
        getRefToFunFromLongName(element)
    } else {
        element.reference?.resolve()
    }

fun printAppExpr(expr: Concrete.Expression, psiElement: ArendArgumentAppExpr, refactoringContext: ChangeSignatureRefactoringContext): IntermediatePrintResult {
    if (expr is Concrete.AppExpression) {
        val resolve = tryResolveFunctionName(expr.function.data as PsiElement)
        val resolveIsInfix = resolve is GlobalReferable && resolve.precedence.isInfix
        val descriptor = if (resolve is Referable) refactoringContext.identifyDescriptor(resolve) else null
        val appExprEntry = AppExpressionEntry(expr, psiElement, refactoringContext, descriptor); appExprEntry.initialize()

        return if (descriptor != null) {
            val processResult = appExprEntry.printUsageEntry(resolve as? GlobalReferable)
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
            val descriptor = if (resolve is Referable) refactoringContext.identifyDescriptor(resolve) else null
            if (isPostfix && descriptor != null) {
                val entry = object: AppExpressionEntry(exprBody, psiElement, refactoringContext, descriptor) {
                    override fun getLambdaParams(parameterMap: Set<ParameterDescriptor>, includingSuperfluousTrailingParams: Boolean): List<ParameterDescriptor> =
                        (this.getParameters().first.firstOrNull { it.isExplicit }?.let { listOf(it) } ?: emptyList()) + super.getLambdaParams(parameterMap, includingSuperfluousTrailingParams)

                    override fun getArguments(): List<ArgumentPrintResult> =
                        listOf(ArgumentPrintResult(IntermediatePrintResult("", null,  null, true, null), isExplicit = true, spacingText = null)) + super.getArguments()
                }
                entry.initialize()
                builder.append(entry.printUsageEntry().first)
                return IntermediatePrintResult(builder.toString(), null, null, false, null)
            }
        }
        builder.append(printAppExpr(exprBody, psiElement, refactoringContext).text)
        return IntermediatePrintResult(builder.toString(), null, null,false, null)
    } else if (expr is Concrete.ReferenceExpression) {
        val d = refactoringContext.identifyDescriptor(expr.referent)
        if (d != null) {
            val text = NoArgumentsEntry(expr, refactoringContext, d).printUsageEntry().first
            return IntermediatePrintResult("(${text})", text, null, !text.startsWith("\\lam"), null)
        } else if ((expr.referent as? GlobalReferable)?.precedence?.isInfix == true) {
            val text = refactoringContext.textGetter(expr.data as PsiElement)
            return IntermediatePrintResult("(${text})", null, null, true, null)
        }
    }

    val (exprData, isAtomic) = getStrippedPsi(expr.data as PsiElement)
    return IntermediatePrintResult(refactoringContext.textGetter(expr.data as PsiElement), refactoringContext.textGetter(exprData), null, isAtomic, null)
}

fun printPattern(pattern: Concrete.Pattern, psiElement: ArendPattern, refactoringContext: ChangeSignatureRefactoringContext): IntermediatePrintResult {
    val isInsideAppExprLeaf = !(pattern.data == psiElement ||
            (pattern.data as? ArendPattern)?.let{ it.constructorReference != null && it.parent == psiElement } == true)

    if (pattern is Concrete.ConstructorPattern && !isInsideAppExprLeaf) {
        val constructor = (pattern.constructor as DataLocatedReferable).data?.element
        val constructorIsInfix = constructor is GlobalReferable && constructor.precedence.isInfix
        val asTextWithWhitespace = (psiElement.asPattern?.getWhitespace(SpaceDirection.LeadingSpace) ?: "") + (psiElement.asPattern?.text ?: "")
        val descriptor = if (constructor is Referable) refactoringContext.identifyDescriptor(constructor) else null
        val patternEntry = PatternEntry(pattern, psiElement, refactoringContext, descriptor)
        patternEntry.initialize()

        return if (descriptor != null) {
            val processResult = patternEntry.printUsageEntry(constructor as? GlobalReferable)
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
                strippedText, null, isAtomic, constructor as? GlobalReferable
            )
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

fun writeFileChangeMap(project: Project, fileChangeMap: HashMap<PsiFile, SortedList<Pair<TextRange, Pair<String, String?>>>>) {
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

fun performTextModification(psi: PsiElement, newElim: String, startPosition : Int = psi.startOffset, endPosition : Int = psi.endOffset) {
    if (!psi.isValid)
        return
    val containingFile = psi.containingFile
    val documentManager = PsiDocumentManager.getInstance(psi.project)
    val document = documentManager.getDocument(containingFile) ?: return
    documentManager.doPostponedOperationsAndUnblockDocument(document)
    document.replaceString(startPosition, endPosition, newElim)
    documentManager.commitDocument(document)
}