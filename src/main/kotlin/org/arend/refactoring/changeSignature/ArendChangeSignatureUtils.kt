package org.arend.refactoring.changeSignature

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.util.elementType
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.containers.SortedList
import org.arend.error.CountingErrorReporter
import org.arend.error.DummyErrorReporter
import org.arend.ext.error.GeneralError
import org.arend.ext.reference.Precedence
import org.arend.ext.variable.Variable
import org.arend.ext.variable.VariableImpl
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.Referable
import org.arend.naming.renamer.StringRenamer
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.ext.*
import org.arend.refactoring.changeSignature.entries.*
import org.arend.resolving.DataLocatedReferable
import org.arend.term.concrete.Concrete
import org.arend.util.ArendBundle
import org.arend.util.appExprToConcrete
import org.arend.util.getBounds
import org.arend.util.patternToConcrete
import java.util.*
import kotlin.collections.ArrayList

class Parameter(val isExplicit: Boolean, val referable: Referable?) {
    override fun toString(): String = "Parameter(isExplicit=${isExplicit}, referable=${referable?.textRepresentation()})"
}
data class NewParameter(val isExplicit: Boolean, val oldParameter: Parameter?)
data class ChangeSignatureRefactoringDescriptor(val affectedDefinition: PsiReferable,
                                                val oldParameters: List<Parameter>,
                                                val newParameters: List<NewParameter>,
                                                val oldParametersWithoutImplicitPrefix: List<Parameter>? = null,
                                                val newParametersWithoutImplicitPrefix: List<NewParameter>? = null,
                                                val newName: String? = null)
data class ChangeSignatureRefactoringContext(val refactoringDescriptors: List<ChangeSignatureRefactoringDescriptor>,
                                             val textReplacements: HashMap<PsiElement, Pair<String, String?>>,
                                             val rangeData: HashMap<Concrete.SourceNode, TextRange>) {
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
            else -> Pair(if (parameterInfo == RenderedParameterKind.INFIX_RIGHT && parameter.printResult.parenthesizedPrefixText != null) parameter.printResult.parenthesizedPrefixText else
                parameter.printResult.strippedText ?: parameter.printResult.text, !parameter.printResult.isAtomic && !inhibitParens)
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
        val defClassMode = entry.descriptor?.affectedDefinition is ArendDefClass

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
            val descriptor = if (resolve is Referable) refactoringContext.identifyDescriptor(resolve) else null
            if (isPostfix && descriptor != null) {
                val entry = object: AppExpressionEntry(exprBody, psiElement, refactoringContext, descriptor) {
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
        builder.append(printAppExpr(exprBody, psiElement, refactoringContext).text)
        return IntermediatePrintResult(builder.toString(), null, null,false, null)
    } else if (expr is Concrete.ReferenceExpression) {
        val d = refactoringContext.identifyDescriptor(expr.referent)
        if (d != null) {
            val text = printUsageEntry(NoArgumentsEntry(expr, refactoringContext, d)).first
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

fun processUsages(project: Project,
                  rootPsiEntries: LinkedHashSet<PsiElement>,
                  referableData: List<ChangeSignatureRefactoringDescriptor>): Boolean /* true if successful, false if aborted */ {
    val concreteSet = LinkedHashSet<ConcreteDataItem>()
    val textReplacements = LinkedHashMap<PsiElement, Pair<String /* Possibly parenthesized */, String? /* Not Parenthesized */>>()
    val fileChangeMap = LinkedHashMap<PsiFile, SortedList<Pair<TextRange, Pair<String, String?>>>>()
    val rangeData = HashMap<Concrete.SourceNode, TextRange>()
    val refactoringTitle = ArendBundle.message("arend.coClause.changeArgumentExplicitness")
    val rootPsiWithErrors = HashSet<PsiElement>()

    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously({
            runReadAction {
                val progressIndicator = ProgressManager.getInstance().progressIndicator
                for ((index, rootPsi) in rootPsiEntries.withIndex()) {
                    progressIndicator.fraction = index.toDouble() / rootPsiEntries.size
                    progressIndicator.checkCanceled()
                    val errorReporter = CountingErrorReporter(GeneralError.Level.ERROR, DummyErrorReporter.INSTANCE)
                    when (rootPsi) {
                        is ArendArgumentAppExpr -> appExprToConcrete(rootPsi, false, errorReporter)?.let{
                            concreteSet.add(ConcreteDataItem(rootPsi, it))
                        }
                        is ArendPattern -> patternToConcrete(rootPsi, errorReporter)?.let {
                            concreteSet.add(ConcreteDataItem(rootPsi, it))
                        }
                        is ArendLocalCoClause -> concreteSet.add(ConcreteDataItem(rootPsi, null))
                    }
                    if (errorReporter.errorsNumber > 0)
                        rootPsiWithErrors.add(rootPsi)
                }

                for ((index, callEntry) in concreteSet.withIndex()) {
                    progressIndicator.fraction = index.toDouble() / concreteSet.size
                    progressIndicator.checkCanceled()
                    val refactoringContext = ChangeSignatureRefactoringContext(referableData, textReplacements, rangeData)
                    rangeData.clear()

                    try {
                        when (callEntry.psi) {
                            is ArendArgumentAppExpr -> {
                                val expr = callEntry.concreteExpr as Concrete.Expression
                                getBounds(expr, callEntry.psi.node.getChildren(null).toList(), rangeData)
                                val printResult = printAppExpr(expr, callEntry.psi, refactoringContext)
                                Pair(printResult.strippedText ?: printResult.text, null)
                            }
                            is ArendPattern -> {
                                val concretePattern = callEntry.concreteExpr as Concrete.ConstructorPattern
                                getBounds(concretePattern, callEntry.psi.node.getChildren(null).toList(), rangeData)
                                val printResult = printPattern(concretePattern, callEntry.psi, refactoringContext)
                                Pair(printResult.text, printResult.strippedText)
                            }
                            is ArendLocalCoClause -> {
                                val referable = callEntry.psi.longName?.resolve as? Referable
                                if (referable != null) {
                                    val descriptor = refactoringContext.identifyDescriptor(referable)
                                    Pair(printUsageEntry(LocalCoClauseEntry(callEntry.psi, refactoringContext, descriptor!!)).first, null)
                                } else throw IllegalStateException()
                            }
                            else -> null
                        }?.let { result ->
                            textReplacements[callEntry.psi] = result
                        }
                    } catch (e: IllegalArgumentException) {
                        rootPsiWithErrors.add(callEntry.psi)
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
        }, refactoringTitle, true, project)) return false

    if (rootPsiWithErrors.size > 0 && Messages.showYesNoDialog(ArendBundle.message("arend.coClause.changeArgumentExplicitness.question1"), refactoringTitle, Messages.getYesButton(), Messages.getNoButton(), Messages.getQuestionIcon()) == Messages.NO) return false

    runWriteAction {
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
    return true
}

fun performTextModification(psi: PsiElement, newElim: String, startPosition : Int = psi.startOffset, endPosition : Int = psi.endOffset) {
    val containingFile = psi.containingFile
    val documentManager = PsiDocumentManager.getInstance(psi.project)
    val document = documentManager.getDocument(containingFile) ?: return
    documentManager.doPostponedOperationsAndUnblockDocument(document)
    document.replaceString(startPosition, endPosition, newElim)
    documentManager.commitDocument(document)
}