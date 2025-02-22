package org.arend.refactoring.changeSignature

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.util.elementType
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.util.containers.SortedList
import org.arend.codeInsight.DefaultParameterDescriptorFactory
import org.arend.codeInsight.ParameterDescriptor
import org.arend.ext.reference.DataContainer
import org.arend.ext.reference.Precedence
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.Referable
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.ext.*
import org.arend.refactoring.NsCmdRefactoringAction
import org.arend.refactoring.changeSignature.entries.*
import org.arend.refactoring.changeSignature.entries.UsageEntry.Companion.RenderedParameterKind
import org.arend.refactoring.rename.ArendRenameProcessor
import org.arend.refactoring.rename.ArendRenameRefactoringContext
import org.arend.term.abs.Abstract.ParametersHolder
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
                DefaultParameterDescriptorFactory.createDataParameter(oldParameter, oldParameter.getExternalScope(), parameter.getNameOrNull(), parameter.typeGetter, oldReferable as? PsiReferable) else
                DefaultParameterDescriptorFactory.createNewParameter(false, oldParameter, oldParameter.getExternalScope(), parameter.getNameOrNull(), parameter.typeGetter)
            newRelevantSegment.add(newParameter)
            relevantReferables.remove(oldReferable)
        }
    }

    for (entry in relevantReferables) {
        val isData = entry.value.isDataParameter
        val newParameter = if (isData)
            DefaultParameterDescriptorFactory.createDataParameter(entry.value, null, null, entry.value.typeGetter, entry.value.oldParameter?.getReferable() as? PsiReferable) else
            DefaultParameterDescriptorFactory.createNewParameter(entry.value.isExplicit, entry.value, null, null, entry.value.typeGetter)
        internalizedSegment.add(newParameter)
    }

    val headExternalSegment = affectedDefinitionOldParameters.subList(0, relevantSegmentStartIndex)
    assert (relevantSegmentEndIndex <= externalSegmentEndIndex)
    val tailExternalSegment = affectedDefinitionOldParameters.subList(relevantSegmentEndIndex, externalSegmentEndIndex)
    val internalSegment = affectedDefinitionOldParameters.subList(externalSegmentEndIndex, affectedDefinitionOldParameters.size)

    val definitionNewSignature =
        DefaultParameterDescriptorFactory.identityTransform(headExternalSegment) +
                newRelevantSegment +
                DefaultParameterDescriptorFactory.identityTransform(tailExternalSegment) +
                internalizedSegment +
                DefaultParameterDescriptorFactory.identityTransform(internalSegment)

    val descriptor = ChangeSignatureRefactoringDescriptor(affectedDefinition, affectedDefinitionOldParameters, definitionNewSignature, null)

    return if (descriptor.isTrivial()) null else descriptor
}

/**
 * This class encodes the context of usages-modifying functionality of ChangeSignatureRefactoring
*  */
data class ChangeSignatureRefactoringContext(val refactoringDescriptors: List<ChangeSignatureRefactoringDescriptor>,
                                             val textReplacements: HashMap<PsiElement, String>,
                                             val rangeData: HashMap<Concrete.SourceNode, TextRange>,
                                             val deferredNsCmds: MutableList<NsCmdRefactoringAction> = ArrayList()) {
    fun textGetter(psi: PsiElement): String =
        textReplacements[psi] ?: buildString {
            if (psi.firstChild == null) append(psi.text) else
                for (c in psi.childrenWithLeaves.toList()) append(textGetter(c))
        }

    fun identifyDescriptor(referable: Referable): ChangeSignatureRefactoringDescriptor? = refactoringDescriptors.firstOrNull {
        it.getAffectedDefinition() == referable
    }
}

data class ConcreteDataItem(val psi: PsiElement,
                            val concreteExpr: Concrete.SourceNode?)
data class IntermediatePrintResult(val text: String,
                                   val parenthesizedPrefixText: String?,
                                   val isAtomic: Boolean,
                                   val isLambda: Boolean,
                                   val referable: GlobalReferable?)
data class ArgumentPrintResult(val printResult: IntermediatePrintResult,
                               val isExplicit: Boolean,
                               val spacingText: String?)

const val FUNCTION_INDEX = -1

fun isIdentifier(text: String) =
    text != "_" && text != "__" &&
    text.matches(Regex("[~!@#$%^&*\\-+=<>?/|\\[\\]:a-zA-Z_\\u2200-\\u22FF\\u2A00-\\u2AFF]([~!@#$%^&*\\-+=<>?/|\\[\\]:a-zA-Z_\\u2200-\\u22FF\\u2A00-\\u2AFF0-9'])*"))

fun printAppExpr(expr: Concrete.Expression, psiElement: ArendArgumentAppExpr, refactoringContext: ChangeSignatureRefactoringContext): IntermediatePrintResult {
    if (expr is Concrete.AppExpression) {
        val underlyingReferable = expr.function.underlyingReferable
        val underlyingReferableIsInfix = underlyingReferable is GlobalReferable && underlyingReferable.precedence.isInfix
        val descriptor = if (underlyingReferable is Referable) refactoringContext.identifyDescriptor(underlyingReferable) else null
        val appExprEntry = AppExpressionEntry(refactoringContext, psiElement, descriptor, expr)

        return if (descriptor != null) {
            appExprEntry.printUsageEntry(underlyingReferable as? GlobalReferable)
        } else {
            val doubleBuilder = DoubleStringBuilder()
            var explicitArgCount = 0
            val isAtomic = if (appExprEntry.blocks.size == 1 && appExprEntry.blocks[0] == FUNCTION_INDEX) getStrippedPsi(expr.function.data as PsiElement).second else false
            val functionName = appExprEntry.getFunctionName()
            val functionIsField = functionName.startsWith(".")

            for (block in appExprEntry.blocks) when (block) {
                is Int -> if (block == FUNCTION_INDEX)
                    doubleBuilder.append(functionName)
                else appExprEntry.getArguments()[block].let {
                    if (it.isExplicit) explicitArgCount++
                    val textInBrackets = "{${it.printResult.text}}"
                    val parameterInfo = when {
                        !appExprEntry.isInfix() -> null
                        explicitArgCount == 1 -> RenderedParameterKind.INFIX_LEFT
                        explicitArgCount == 2 -> RenderedParameterKind.INFIX_RIGHT
                        else -> null
                    }
                    val inhibitParens = if (!it.printResult.isLambda && it.printResult.referable != null && parameterInfo != null && underlyingReferable is GlobalReferable) {
                        if (it.printResult.referable == underlyingReferable) {
                            parameterInfo == RenderedParameterKind.INFIX_LEFT && underlyingReferable.precedence.associativity == Precedence.Associativity.LEFT_ASSOC ||
                                    parameterInfo == RenderedParameterKind.INFIX_RIGHT && underlyingReferable.precedence.associativity == Precedence.Associativity.RIGHT_ASSOC
                        } else {
                            it.printResult.referable.precedence.priority > underlyingReferable.precedence.priority
                        }
                    } else false

                    val text =
                        if (it.isExplicit || block == 0 && functionIsField) (
                            if (explicitArgCount == 2 && underlyingReferableIsInfix && it.printResult.parenthesizedPrefixText != null)
                                it.printResult.parenthesizedPrefixText
                            else if (it.printResult.isAtomic || inhibitParens)
                                it.printResult.text
                            else "(${it.printResult.text})")
                        else textInBrackets

                    val parenthesizedPrefixText = if (it.isExplicit && underlyingReferableIsInfix && it.printResult.parenthesizedPrefixText != null) it.printResult.parenthesizedPrefixText else text

                    doubleBuilder.append(text, parenthesizedPrefixText)
                }
                is PsiElement -> doubleBuilder.append(refactoringContext.textGetter(block))
            }
            IntermediatePrintResult(
                doubleBuilder.defaultBuilder.toString().let { if (isAtomic && underlyingReferableIsInfix) "($it)" else it},
                doubleBuilder.alternativeBuilder.toString(),
                isAtomic,
                false,
                underlyingReferable as? GlobalReferable)
        }
    } else if (expr is Concrete.LamExpression && expr.data == null) {
        val builder = StringBuilder()
        val exprBody = expr.body
        if (exprBody is Concrete.AppExpression) {
            val function = exprBody.function.data as PsiElement
            val underlyingReferable = exprBody.underlyingReferable
            val isPostfix = (function as ArendIPName).postfix != null
            val descriptor = if (underlyingReferable is Referable) refactoringContext.identifyDescriptor(underlyingReferable) else null
            if (isPostfix && descriptor != null) {
                val entry = object: AppExpressionEntry(refactoringContext, psiElement, descriptor, exprBody) {
                    override fun getLambdaParams(parameterMap: Set<ParameterDescriptor>, includingSuperfluousTrailingParams: Boolean): List<ParameterDescriptor> =
                        (this.getOldParameters().firstOrNull { it.isExplicit }?.let { listOf(it) } ?: emptyList()) + super.getLambdaParams(parameterMap, includingSuperfluousTrailingParams)

                    override fun getArguments(): List<ArgumentPrintResult> =
                        listOf(ArgumentPrintResult(IntermediatePrintResult("",   null,
                            isAtomic = true,
                            isLambda = false,
                            referable = null
                        ), isExplicit = true, spacingText = null)) + super.getArguments()
                }
                builder.append(entry.printUsageEntry().text)
                return IntermediatePrintResult(builder.toString(),  null,
                    isAtomic = false,
                    isLambda = true,
                    referable = null
                )
            }
        }
        builder.append(printAppExpr(exprBody, psiElement, refactoringContext).text)
        return IntermediatePrintResult(builder.toString(),  null, isAtomic = false, isLambda = true, referable = null)
    } else if (expr is Concrete.ReferenceExpression) {
        val d = refactoringContext.identifyDescriptor(expr.referent)
        if (d != null) {
            return NoArgumentsEntry(expr, refactoringContext, d).printUsageEntry(expr.referent as? GlobalReferable)
        } else if ((expr.referent as? GlobalReferable)?.precedence?.isInfix == true) {
            val text = refactoringContext.textGetter(expr.data as PsiElement)
            return IntermediatePrintResult("(${text})",  null, isAtomic = true, isLambda = false, referable = null)
        } else if (expr.data is ArendLongName) {
            val target = expr.underlyingReferable
            val contextName = if (target is PsiLocatedReferable) UsageEntry.getContextName(target, psiElement, refactoringContext) else target.refName
            return IntermediatePrintResult(contextName, contextName,
                isAtomic = true,
                isLambda = false,
                referable = null
            )
        }
    }

    val (exprData, isAtomic) = getStrippedPsi(expr.data as PsiElement)
    return IntermediatePrintResult(refactoringContext.textGetter(exprData), null, isAtomic, false, null)
}

fun printPattern(pattern: Concrete.Pattern, psiElement: ArendPattern, refactoringContext: ChangeSignatureRefactoringContext): IntermediatePrintResult {
    val isInsideAppExprLeaf = !(pattern.data == psiElement ||
            (pattern.data as? ArendPattern)?.let{ (it.singleReferable != null || it.constructorReference != null) && it.parent == psiElement } == true)

    if (pattern is Concrete.ConstructorPattern && !isInsideAppExprLeaf) {
        val constructor = (pattern.constructor as? DataContainer)?.data
        val constructorIsInfix = constructor is GlobalReferable && constructor.precedence.isInfix
        val asTextWithWhitespace = (psiElement.asPattern?.getWhitespace(SpaceDirection.LeadingSpace) ?: "") + (psiElement.asPattern?.text ?: "")
        val descriptor = if (constructor is Referable) refactoringContext.identifyDescriptor(constructor) else null
        val patternEntry = PatternEntry(refactoringContext, psiElement, descriptor, pattern)

        return if (descriptor != null) {
            val processResult = patternEntry.printUsageEntry(constructor as? GlobalReferable)
            val baseText = processResult.text + asTextWithWhitespace
            IntermediatePrintResult(baseText, processResult.parenthesizedPrefixText,
                isAtomic = false,
                isLambda = false,
                referable = constructor as? GlobalReferable
            )
        } else {
            val builder = StringBuilder()
            var explicitArgCount = 0
            val processedFunction = refactoringContext.textGetter(pattern.constructorData as ArendPattern)
            val (strippedFunc, isAtomic) = if (patternEntry.blocks.size == 1 && patternEntry.blocks[0] == FUNCTION_INDEX) getStrippedPsi(pattern.data as PsiElement) else Pair(null, false)
            val strippedText = strippedFunc?.let { refactoringContext.textGetter(it) }

            for (block in patternEntry.blocks) when (block) {
                is Int -> if (block == FUNCTION_INDEX) builder.append(processedFunction) else patternEntry.getArguments()[block].let {
                    if (it.isExplicit) explicitArgCount++
                    val textInBrackets = "{${it.printResult.text}}"
                    val text = if (it.isExplicit) (if (explicitArgCount == 2 && constructorIsInfix && it.printResult.parenthesizedPrefixText != null) it.printResult.parenthesizedPrefixText else it.printResult.text) else textInBrackets
                    builder.append(text)
                }
                is PsiElement -> builder.append(refactoringContext.textGetter(block))
            }
            IntermediatePrintResult(strippedText ?: builder.toString().let { if (isAtomic && constructorIsInfix) "($it)" else it}, null, isAtomic, false, constructor as? GlobalReferable
            )
        }
    }

    fun textGetterForPatterns(exprData: PsiElement, preferStrippedVersion: Boolean): String {
        val replacementTextEntry = refactoringContext.textReplacements[exprData]
        if (replacementTextEntry != null) {
            if (!preferStrippedVersion && exprData is ArendPattern && !exprData.isExplicit) return "{$replacementTextEntry}"
            return replacementTextEntry
        }
        if (exprData.firstChild == null) return exprData.text
        val childRange = if (exprData is ArendPattern && !exprData.isExplicit) {
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
    return IntermediatePrintResult(textGetterForPatterns(exprData, true), null, isAtomic, false, null)
}

fun renameParameters(project: Project, changeInfo: ArendChangeInfo, parametersHolder: ParametersHolder) {
    val defIdentifiers: List<PsiElement> = parametersHolder.parameters.map { tele ->
        when (tele) {
            is ArendNameTele -> tele.identifierOrUnknownList.mapNotNull { iou -> iou.defIdentifier }
            is ArendTypeTele -> tele.typedExpr?.identifierOrUnknownList?.mapNotNull { iou -> iou.defIdentifier } ?: emptyList()
            is ArendFieldTele -> tele.referableList
            else -> throw IllegalStateException()
        }
    }.flatten()
    val processors = ArrayList<Pair<List<SmartPsiElementPointer<PsiElement>>, ArendRenameProcessor>>()
    for (p in changeInfo.newParameters) {
        val d = if (p.oldIndex != -1) defIdentifiers[p.oldIndex] else null
        if (d != null && p.name != d.text) {
            val renameProcessor = ArendRenameProcessor(project, d, p.name, ArendRenameRefactoringContext(d.text), null)
            val usages = renameProcessor.findUsages()
            processors.add(Pair(usages.mapNotNull{ it.element }.map { SmartPointerManager.getInstance(project).createSmartPsiElementPointer(it) }.toList(), renameProcessor))
        }
    }
    for (p in processors)
        p.second.executeEx(p.first.mapNotNull {
            it.element as? ArendRefIdentifier
        }.map {
            MoveRenameUsageInfo(it.reference, p.second.element)
        }.toTypedArray())
}

fun writeFileChangeMap(project: Project, fileChangeMap: HashMap<PsiFile, SortedList<Pair<TextRange, String>>>):
        Map<PsiFile, MutableList<TextRange>> {
    val docManager = PsiDocumentManager.getInstance(project)
    val result = HashMap<PsiFile, MutableList<TextRange>>()
    for (changeEntry in fileChangeMap) {
        val textFile = docManager.getDocument(changeEntry.key)
        if (textFile != null) {
            val replacements = ArrayList<TextRange>()
            for (replacementEntry in changeEntry.value.reversed()) {
                val textRange = replacementEntry.first
                val delta = replacementEntry.second.length - textRange.endOffset + textRange.startOffset
                textFile.replaceString(textRange.startOffset, textRange.endOffset, replacementEntry.second)
                for (i in 0 until replacements.size) replacements[i] = TextRange(replacements[i].startOffset + delta, replacements[i].endOffset + delta)
                replacements.add(TextRange(textRange.startOffset, textRange.endOffset + delta))
            }
            docManager.commitDocument(textFile)
            result[changeEntry.key] = replacements
        }
    }
    return result
}

fun performTextModification(psi: PsiElement, newText: String, startPosition : Int = psi.startOffset, endPosition : Int = psi.endOffset) {
    if (!psi.isValid)
        return
    val containingFile = psi.containingFile
    val documentManager = PsiDocumentManager.getInstance(psi.project)
    val document = documentManager.getDocument(containingFile) ?: return
    documentManager.doPostponedOperationsAndUnblockDocument(document)
    document.replaceString(startPosition, endPosition, newText)
    documentManager.commitDocument(document)
}