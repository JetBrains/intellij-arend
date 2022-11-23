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
import com.intellij.psi.PsiReference
import com.intellij.psi.search.LocalSearchScope
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
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.naming.scope.ClassFieldImplScope
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.PIPE
import org.arend.psi.ext.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.PsiReferable
import org.arend.quickfix.referenceResolve.ResolveReferenceAction
import org.arend.refactoring.*
import org.arend.resolving.ArendReferableConverter
import org.arend.search.ClassDescendantsSearch
import org.arend.term.abs.Abstract.Pattern
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.PrettyPrintVisitor
import org.arend.util.ArendBundle
import org.arend.util.appExprToConcrete
import org.arend.util.getBounds
import java.util.Collections.singletonList

data class ProcessAppExprResult(val text: String, val strippedText: String?, val parenthesizedPrefixTest: String?, val isAtomic: Boolean, val referable: GlobalReferable?)
data class ArgumentDescriptor(val text: String, val strippedText: String?, val parenthesizedPrefixText: String?, val isExplicit: Boolean, val isAtomic: Boolean, val spacingText: String?, val referable: GlobalReferable?) {
   constructor (processResult: ProcessAppExprResult, isExplicit: Boolean, spaceText: String?) : this(processResult.text, processResult.strippedText, processResult.parenthesizedPrefixTest, isExplicit, processResult.isAtomic, spaceText, processResult.referable)
}

abstract class UsageEntry(val refactoringContext: RefactoringContext, val contextPsi: ArendCompositeElement) {
    abstract fun getArguments(): List<ArgumentDescriptor>

    fun getTrailingParameters(): List<Parameter> {
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
    abstract fun getDefName(): String

    open fun getUnmodifiablePrefix(): String? = null
    open fun getUnmodifiableSuffix(): String? = null
}

const val FUNCTION_INDEX = -1

open class AppExpressionEntry(val expr: Concrete.AppExpression, val argumentAppExpr: ArendArgumentAppExpr, val functionName: String, refactoringContext: RefactoringContext): UsageEntry(refactoringContext, argumentAppExpr) {
    private val exprPsi: List<PsiElement>? = refactoringContext.rangeData[expr]?.let { range -> argumentAppExpr.childrenWithLeaves.toList().filter { range.contains(it.textRange) }}
    private val procArguments = ArrayList<ArgumentDescriptor>()
    val procFunction: String
    val isDotExpression: Boolean
    val isInfixNotation: Boolean
    val blocks: ArrayList<Any>

    init {
        if (exprPsi == null) throw IllegalStateException()
        blocks = ArrayList(exprPsi)
        fun getBlocksInRange (range: TextRange): List<PsiElement> = blocks.filterIsInstance<PsiElement>().filter { range.contains(it.textRange) }
        val replacementMap = HashMap<Pair<PsiElement, PsiElement>, Int>()

        val functionBlock = getBlocksInRange(refactoringContext.rangeData[expr.function]!!).let{ if (it.size != 1)
            throw IllegalStateException() else it.first()}
        val argumentsWhichActuallyOccurInText = ArrayList<Concrete.Argument>()
        var firstExplicit : Int? = null

        for (argument in expr.arguments) refactoringContext.rangeData[argument.expression]?.let { argRange ->
            if (firstExplicit == null && argument.isExplicit) firstExplicit = argumentsWhichActuallyOccurInText.size
            replacementMap[getBlocksInRange(argRange).let{ Pair(it.first(), it.last()) }] = argumentsWhichActuallyOccurInText.size
            argumentsWhichActuallyOccurInText.add(argument)
        }
        isDotExpression = replacementMap.contains(Pair(functionBlock, functionBlock))
        replacementMap[Pair(functionBlock, functionBlock)] = FUNCTION_INDEX

        procFunction = textGetter(expr.function.data as PsiElement, refactoringContext.textReplacements)
        for (e in replacementMap) {
            val sI = blocks.indexOf(e.key.first)
            val eI = blocks.indexOf(e.key.second)
            if (sI == -1 || eI == -1) throw IllegalStateException()
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
            procArguments.add(ArgumentDescriptor(processAppExpr(argument.expression, argumentAppExpr, functionName, refactoringContext), argument.isExplicit, spacingMap[index]))

        isInfixNotation = blocks.indexOf(FUNCTION_INDEX) != 0
    }

    override fun getArguments(): List<ArgumentDescriptor> = procArguments

    override fun getParameters(): Pair<List<Parameter>, List<NewParameter>> = Pair(refactoringContext.oldParameters, refactoringContext.newParameters.let { if (isDotExpression) it.drop(1) else it })

    override fun getDefName(): String = if (isDotExpression) procFunction else functionName //TODO: Fixme (we should remove backticks in certain situations)
}

class NoArgumentsEntry(refExpr: Concrete.ReferenceExpression, refactoringContext: RefactoringContext, val definitionName: String): UsageEntry(refactoringContext, refExpr.data as ArendCompositeElement) {
    override fun getArguments(): List<ArgumentDescriptor> = emptyList()

    override fun getParameters(): Pair<List<Parameter>, List<NewParameter>> = Pair(refactoringContext.oldParameters, refactoringContext.newParameters)

    override fun getDefName(): String = definitionName
}

class PatternEntry(pattern: ArendPattern, refactoringContext: RefactoringContext, val definitionName: String): UsageEntry(refactoringContext, pattern) {
    private val procArguments = ArrayList<ArgumentDescriptor>()
    init {
        val spacingMap = HashMap<Pattern, String>()
        var buffer = ""
        for (block in pattern.childrenWithLeaves) {
            if (block is Pattern) {
                spacingMap[block] = buffer
                buffer = ""
            } else if (!pattern.children.contains(block)) buffer += block.text
        }

        var concrete = ConcreteBuilder.convertPattern(pattern, ArendReferableConverter, DummyErrorReporter.INSTANCE, null)
        val list = mutableListOf(concrete)
        ExpressionResolveNameVisitor(ArendReferableConverter, pattern.scope, mutableListOf(), DummyErrorReporter.INSTANCE, null).visitPatterns(list, mutableMapOf(), true)
        concrete = list.single()

        for (arg in concrete.patterns) {
            val data = arg.data as PsiElement
            val data1: PsiElement? = data
            val text = textGetter(data, refactoringContext.textReplacements)
            val strippedText = if (data1 != null) textGetter(data1, refactoringContext.textReplacements) else null
            val isAtomic = data1 is ArendDefIdentifier || data1 is ArendPattern && (data1.singleReferable != null || data1.isTuplePattern || data1.isUnnamed)
            procArguments.add(ArgumentDescriptor(text, strippedText, null, arg.isExplicit, isAtomic, spacingMap[arg.data as Pattern], null))
        }
    }

    override fun getLambdaParams(parameterMap: Set<Parameter>, includingSuperfluousTrailingParams: Boolean): List<Parameter> = emptyList()

    override fun getArguments(): List<ArgumentDescriptor> = procArguments

    override fun getParameters(): Pair<List<Parameter>, List<NewParameter>> = Pair(refactoringContext.oldParametersConstructor!!, refactoringContext.newParametersConstructor!!)

    override fun getDefName(): String = definitionName
}

class LocalCoClauseEntry(private val localCoClause: ArendLocalCoClause, refactoringContext: RefactoringContext, val definitionName: String): UsageEntry(refactoringContext, localCoClause) {
    private val procArguments = ArrayList<ArgumentDescriptor>()
    init {
        val spacingMap = HashMap<ArendLamParam, String>()
        var buffer = ""
        for (block in localCoClause.childrenWithLeaves) {
            if (block is ArendLamParam) {
                spacingMap[block] = buffer
                buffer = ""
            } else if (!localCoClause.children.contains(block) &&
                block.elementType != PIPE &&
                block.prevSibling?.let{ it.elementType == PIPE} != true) buffer += block.text
        }

        for (arg in localCoClause.lamParamList) {
            val data = arg.data as PsiElement
            var data1: PsiElement? = data
            while (true) {
                if (data1 is ArendNameTele && data1.identifierOrUnknownList.size == 1 && data1.type == null) {
                    data1 = data1.identifierOrUnknownList[0]; continue
                }
                break
            }
            val text = textGetter(data, refactoringContext.textReplacements)
            val strippedText = if (data1 != null) textGetter(data1, refactoringContext.textReplacements) else null
            val isExplicit = when (data) {
                is ArendNameTele -> data.isExplicit
                is ArendPattern -> data.isExplicit
                else -> data.text.trim().startsWith("{")
            }
            procArguments.add(ArgumentDescriptor(text, strippedText, null, isExplicit, data1 is ArendIdentifierOrUnknown, spacingMap[arg], null))
        }
    }
    override fun getLambdaParams(parameterMap: Set<Parameter>, includingSuperfluousTrailingParams: Boolean): List<Parameter> = emptyList()

    override fun getArguments(): List<ArgumentDescriptor> = procArguments

    override fun getParameters(): Pair<List<Parameter>, List<NewParameter>> = Pair(refactoringContext.oldParameters, refactoringContext.newParameters)

    override fun getUnmodifiableSuffix(): String? {
        val children = localCoClause.childrenWithLeaves.toList()
        val index = localCoClause.fatArrow?.let { children.indexOf(it) } ?: -1
        return if (index != -1) buildString {
          for (c in children.subList(index - 1, children.size)) append(textGetter(c, refactoringContext.textReplacements))
        } else null
    }

    override fun getUnmodifiablePrefix(): String = buildString {
        val children = localCoClause.childrenWithLeaves.toList()
        val index = localCoClause.longName?.let{ children.indexOf(it) } ?: 1
        for (c in children.subList(0, index)) append(textGetter(c, refactoringContext.textReplacements))
    }

    override fun getDefName(): String = definitionName
}

data class Parameter(val isExplicit: Boolean, val referable: Referable?)
data class NewParameter(val isExplicit: Boolean, val oldParameter: Parameter?)
data class RefactoringContext(
    val def: PsiElement,
    val oldParameters: List<Parameter>, val newParameters: List<NewParameter>,
    val oldParametersConstructor: List<Parameter>?, val newParametersConstructor: List<NewParameter>?, /* These 2 fields make sense only for ArendConstructor */
    val textReplacements: HashMap<PsiElement, String>,
    val rangeData: HashMap<Concrete.Expression, TextRange>)

private enum class RenderedParameterKind {INFIX_LEFT, INFIX_RIGHT}

fun doProcessEntry(entry: UsageEntry, globalReferable: GlobalReferable? = null): Pair<String /* default version */, String? /* version with parenthesized operator */ > {
    val defaultBuilder = StringBuilder()
    val parenthesizedPrefixBuilder = StringBuilder()
    fun append(text: String) { defaultBuilder.append(text); parenthesizedPrefixBuilder.append(text) }

    val (oldParameters, newParameters) = entry.getParameters()

    entry.getUnmodifiablePrefix()?.let { defaultBuilder.append(it); parenthesizedPrefixBuilder.append(it) }

    var i = 0
    var j = 0
    val parameterMap = HashMap<Parameter, ArgumentDescriptor?>()
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
        } else throw IllegalArgumentException()
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

    val infixNotationSupported = newParameters.withIndex().filter { it.value.isExplicit }.let { explicitParams -> explicitParams.size == 2 && explicitParams.all { it.index >= newParameters.size - 2 } }

    fun renderParameter(oldParam: Parameter?, isExplicit: Boolean, parameterInfo: RenderedParameterKind? = null): Pair<String /* text */, String /* spacing */> {
        val parameter = oldParam?.let{ parameterMap[oldParam] }
        val referable = parameter?.referable
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
            else -> Pair(parameter.strippedText ?: parameter.text, !parameter.isAtomic && !inhibitParens)
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


    if (infixNotationSupported && entry is AppExpressionEntry && entry.isInfixNotation && lambdaArgs == "") {
        //if it is not isInfix then use backtick!
        val (leftParam, rightParam) = newParameters.filter { it.isExplicit }.toList().let { Pair(it[0], it[1]) }
        val (leftText, leftSpacing) = renderParameter(leftParam.oldParameter, isExplicit = true, RenderedParameterKind.INFIX_LEFT)
        val (rightText, rightSpacing) = renderParameter(rightParam.oldParameter, isExplicit = true, RenderedParameterKind.INFIX_RIGHT)

        val defName = entry.getDefName()  //TODO: Fixme; we should take backticks into account
        append("$leftText$leftSpacing$defName")

        printParams(newParameters.filter { !it.isExplicit })
        append("$rightSpacing$rightText")
    } else {
        val defClassMode = entry.refactoringContext.def is ArendDefClass

        if (lambdaArgs != "" && !defClassMode) append("\\lam$lambdaArgs => ")
        for (e in oldArgToLambdaArgMap) parameterMap[e.key] = ArgumentDescriptor(e.value, null, null, isExplicit = true, isAtomic = true, spacingText = null, referable = null)

        val defName = entry.getDefName()
        defaultBuilder.append(defName); parenthesizedPrefixBuilder.append(if (globalReferable?.precedence?.isInfix == true) "($defName)" else defName)

        printParams(newParameters.filter { !defClassMode || !oldArgToLambdaArgMap.keys.contains(it.oldParameter) })

        while (j < entry.getArguments().size) {
            append(" ${entry.getArguments()[j].text}")
            j++
        }
    }

    entry.getUnmodifiableSuffix()?.let {
        append(it)
    }
    return Pair(defaultBuilder.toString(), if (lambdaArgs == "") parenthesizedPrefixBuilder.toString() else null)
}

fun getStrippedPsi(exprData2: PsiElement): Pair<PsiElement, Boolean> {
    var exprData = exprData2
    stripLoop@while (true) {
        when (val exprData1 = exprData) {
            is ArendImplicitArgument -> if (exprData1.tupleExprList.size == 1) {
                exprData = exprData1.tupleExprList[0]; continue
            }
            is ArendAtomArgument -> {
                exprData = exprData1.atomFieldsAcc; continue
            }
            is ArendAtomFieldsAcc -> if (exprData1.numberList.isEmpty()) {
                exprData = exprData1.atom; continue
            }
            is ArendTuple -> if (exprData1.tupleExprList.size == 1) {
                exprData = exprData1.tupleExprList[0]; continue
            }
            is ArendTupleExpr -> if (exprData1.type == null) {
                exprData = exprData1.expr; continue
            }
            is ArendAtom -> {
                val child = exprData1.firstRelevantChild
                if (child is ArendTuple || child is ArendLiteral) {
                    exprData = child; continue
                }
            }
            is ArendNewExpr -> if (exprData1.appExpr?.let { it.textRange == exprData1.textRange } == true) {
                exprData = exprData1.appExpr!!; continue
            }
            is ArendArgumentAppExpr -> if (exprData1.argumentList.isEmpty() && exprData1.atomFieldsAcc != null) {
                exprData = exprData1.atomFieldsAcc!!; continue
            }
        }
        break
    }
    return Pair(exprData, exprData is ArendAtom || exprData is ArendLongName || exprData is ArendTuple || exprData is ArendLiteral || exprData.text == "_" || exprData is ArendAtomFieldsAcc)
}

fun processAppExpr(expr: Concrete.Expression, psiElement: ArendArgumentAppExpr, defName: String, refactoringContext: RefactoringContext): ProcessAppExprResult {
    if (expr is Concrete.AppExpression) {
        val function = expr.function.data as PsiElement
        val resolve = tryResolveFunctionName(function)
        val resolveIsInfix = resolve is GlobalReferable && resolve.precedence.isInfix
        val appExprEntry = AppExpressionEntry(expr, psiElement, defName, refactoringContext)

        return if (refactoringContext.def == resolve) {
            val processResult = doProcessEntry(appExprEntry, resolve as? GlobalReferable)
            val isLambda = processResult.second == null
            ProcessAppExprResult(if (isLambda) "(${processResult.first})" else processResult.first, if (isLambda) processResult.first else null, processResult.second,false, resolve as? GlobalReferable)
        } else {
            val builder = StringBuilder()
            val parenthesizedBuilder = StringBuilder()
            fun append(text: String) { builder.append(text); parenthesizedBuilder.append(text) }

            val processedFunction = appExprEntry.procFunction
            var explicitArgCount = 0
            val isAtomic = if (appExprEntry.blocks.size == 1 && appExprEntry.blocks[0] == FUNCTION_INDEX) getStrippedPsi(expr.function.data as PsiElement).second else false

            for (block in appExprEntry.blocks) when (block) {
                is Int -> if (block == FUNCTION_INDEX) append(processedFunction) else appExprEntry.getArguments()[block].let {
                    if (it.isExplicit) explicitArgCount++
                    val textInBrackets = "{${it.strippedText ?: it.text}}"
                    val text = if (it.isExplicit) (if (explicitArgCount == 2 && resolveIsInfix && it.parenthesizedPrefixText != null) it.parenthesizedPrefixText else it.text) else textInBrackets
                    val parenthesizedText = if (it.isExplicit) (if (resolveIsInfix && it.parenthesizedPrefixText != null) it.parenthesizedPrefixText else it.text) else textInBrackets
                    builder.append(text); parenthesizedBuilder.append(parenthesizedText)
                }
                is PsiElement -> append(textGetter(block, refactoringContext.textReplacements))
            }
            ProcessAppExprResult(builder.toString().let { if (isAtomic && resolveIsInfix) "($it)" else it}, null, parenthesizedBuilder.toString(), isAtomic, resolve as? GlobalReferable)
        }
    } else if (expr is Concrete.LamExpression && expr.data == null) {
        val builder = StringBuilder()
        val exprBody = expr.body
        if (exprBody is Concrete.AppExpression) {
            val function = exprBody.function.data as PsiElement
            val resolve = tryResolveFunctionName(function)
            val isPostfix = (function as ArendIPName).postfix != null
            if (isPostfix && refactoringContext.def == resolve) {
                builder.append(doProcessEntry(object: AppExpressionEntry(exprBody, psiElement, defName, refactoringContext) {
                    override fun getLambdaParams(parameterMap: Set<Parameter>, includingSuperfluousTrailingParams: Boolean): List<Parameter> =
                        (this.getParameters().first.firstOrNull { it.isExplicit }?.let { listOf(it) } ?: emptyList()) + super.getLambdaParams(parameterMap, includingSuperfluousTrailingParams)

                    override fun getArguments(): List<ArgumentDescriptor> = listOf(ArgumentDescriptor("", null,  null, isExplicit = true, isAtomic = true, spacingText = null, referable = null)) + super.getArguments()
                }).first)
                return ProcessAppExprResult(builder.toString(), null, null, false, null)
            }
        }
        builder.append(processAppExpr(exprBody, psiElement, defName, refactoringContext).text)
        return ProcessAppExprResult(builder.toString(), null, null,false, null)
    } else if (expr is Concrete.ReferenceExpression) {
        if (expr.referent == refactoringContext.def) {
            val text = doProcessEntry(NoArgumentsEntry(expr, refactoringContext, defName)).first
            return ProcessAppExprResult("(${text})", text, null, true, null)
        } else if ((expr.referent as? GlobalReferable)?.precedence?.isInfix == true) {
            val text = textGetter(expr.data as PsiElement, refactoringContext.textReplacements)
            return ProcessAppExprResult("(${text})", null, null, true, null)
        }
    }

    val (exprData, isAtomic) = getStrippedPsi(expr.data as PsiElement)

    return ProcessAppExprResult(textGetter(expr.data as PsiElement, refactoringContext.textReplacements),
        textGetter(exprData, refactoringContext.textReplacements), null, isAtomic, null)
}

fun textGetter(psi: PsiElement, textReplacements: HashMap<PsiElement, String>): String = textReplacements[psi] ?: buildString {
    if (psi.firstChild == null) append(psi.text) else
        for (c in psi.childrenWithLeaves)
            append(textGetter(c, textReplacements))
}

class RefactoringDescriptor(val def: PsiReferable, val oldParameters: List<Parameter>, val newParameters: List<NewParameter>)

class ChangeArgumentExplicitnessIntention : SelfTargetingIntention<ArendCompositeElement>(
    ArendCompositeElement::class.java,
    ArendBundle.message("arend.coClause.changeArgumentExplicitness")
) {
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

        val refactoringDescriptors = HashSet<RefactoringDescriptor>()
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
                refactoringDescriptors.add(RefactoringDescriptor(clazz, oldParameters, oldParameters.map { NewParameter(if (switchedFieldIdentifiers.contains(it.referable)) !it.isExplicit else it.isExplicit, it) }))
            }

            NameFieldApplier(project).applyTo(refactoringDescriptors)
        } else {
            if (switchedArgIndexInTele == null || switchedArgIndexInTele == -1) {
                val teleSize = if (element is ArendTypeTele && element.typedExpr?.identifierOrUnknownList?.isEmpty() == true) 1 else getTele(element)?.size ?: return
                chooseApplier(element)?.applyTo(def, (0 until teleSize).map { it + teleIndexInDef }.toSet())
            } else {
                chooseApplier(element)?.applyTo(def, singletonList(switchedArgIndexInTele + teleIndexInDef).toSet())
            }
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
        val argsText = getTele(tele)?.map { it.text }
        return if (argsText?.size == 1) 0 else argsText?.indexOf(switchedArg.text)
    }

    private fun switchTeleExplicitness(tele: ArendCompositeElement) {
        val def = tele.ancestor<PsiReferable>() as PsiElement
        val teleIndex = def.children.indexOf(tele)
        val anchor = def.children[teleIndex - 1]
        val factory = ArendPsiFactory(tele.project)
        val newTele = createSwitchedTele(factory, tele) ?: return
        def.children[teleIndex].deleteWithNotification()
        val inserted = def.addAfterWithNotification(newTele, anchor)
        val ws = factory.createWhitespace(" ")
        def.addBeforeWithNotification(ws, inserted)
    }
}

abstract class ChangeArgumentExplicitnessApplier(val project: Project) {
    protected val factory = ArendPsiFactory(project)

    /**
     * Rewrites all usages and definition
     *
     * @param def definition to which this refactoring is applied
     * @param indexInDef Indices whose explicitness is to be switched
     * @return  new telescope in definition
     */


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

        val rd = RefactoringDescriptor(def, oldParameters, newParameters)
        applyTo(singletonList(rd).toSet(), oldParametersConstructor, newParametersConstructor)
    }

    fun applyTo(referableData: Set<RefactoringDescriptor>, oldParametersConstructor: List<Parameter>? = null, newParametersConstructor: List<NewParameter>? = null) {
        val fCallParents: LinkedHashSet<Pair<PsiElement, RefactoringDescriptor>> = referableData.map { descriptor ->
            ReferencesSearch.search(descriptor.def).map {
                Pair(it.element, descriptor)
            }.filter {
                when (val psi = it.first) {
                    is ArendRefIdentifier -> (psi.parent as? ArendLongName)?.let { longName -> longName.refIdentifierList.lastOrNull() == psi } ?: true
                    else -> true
                }
        }.map { Pair(getParentPsiFunctionCall(it.first), it.second )} }.flatten().sortedWith { a, b -> if (a.first.textLength == b.first.textLength) b.first.startOffset.compareTo(a.first.startOffset) else a.first.textLength.compareTo(b.first.textLength)  }.toCollection(LinkedHashSet())

        val concreteSet = LinkedHashSet<Pair<Pair<PsiElement, RefactoringDescriptor>, Concrete.Expression?>>()
        val textReplacements = LinkedHashMap<PsiElement, String>()
        val fileChangeMap = LinkedHashMap<PsiFile, SortedList<Pair<TextRange, String>>>()
        val rangeData = HashMap<Concrete.Expression, TextRange>()
        val refactoringContexts = HashMap<RefactoringDescriptor, RefactoringContext>()
        referableData.forEach { d -> refactoringContexts[d] = RefactoringContext(d.def, d.oldParameters, d.newParameters, oldParametersConstructor, newParametersConstructor, textReplacements, rangeData) }
        val refactoringTitle = ArendBundle.message("arend.coClause.changeArgumentExplicitness")
        val failingAppExprs = HashSet<Pair<ArendArgumentAppExpr, PsiReferable>>()

        if (!ProgressManager.getInstance().runProcessWithProgressSynchronously({
            runReadAction {
                val progressIndicator = ProgressManager.getInstance().progressIndicator
                for ((index, fCallEntry) in fCallParents.withIndex()) {
                    val (fCall, d) = fCallEntry
                    progressIndicator.fraction = index.toDouble() / fCallParents.size
                    progressIndicator.checkCanceled()
                    when (fCall) {
                        is ArendArgumentAppExpr -> {
                            val errorReporter = CountingErrorReporter(GeneralError.Level.ERROR, DummyErrorReporter.INSTANCE)
                            appExprToConcrete(fCall, false, errorReporter)?.let{
                                concreteSet.add(Pair(Pair(fCall, d), it))
                            }
                            if (errorReporter.errorsNumber > 0) failingAppExprs.add(Pair(fCall, d.def))
                        }
                        is ArendPattern, is ArendLocalCoClause -> concreteSet.add(Pair(Pair(fCall, d), null))
                    }
                }

                for ((index, callEntry) in concreteSet.withIndex()) {
                    progressIndicator.fraction = index.toDouble() / concreteSet.size
                    progressIndicator.checkCanceled()
                    val psiElement = callEntry.first.first
                    val descriptor = callEntry.first.second
                    val refactoringContext = refactoringContexts[descriptor]!! // safe to write
                    val defName = if (descriptor.def is PsiLocatedReferable && psiElement is ArendCompositeElement) ResolveReferenceAction.getTargetName(descriptor.def, psiElement)!! else descriptor.def.refName

                    when (psiElement) {
                        is ArendArgumentAppExpr -> {
                            try {
                                val expr = callEntry.second!!
                                refactoringContext.rangeData.clear()
                                getBounds(expr, psiElement.node.getChildren(null).toList(), rangeData)
                                processAppExpr(expr, psiElement, defName, refactoringContext).let { it.strippedText ?: it.text }
                            } catch (e: IllegalStateException) { // TODO: We could use custom exception in processAppExpr
                                failingAppExprs.add(Pair(psiElement, descriptor.def))
                                null
                            }
                        }
                        is ArendPattern -> doProcessEntry(PatternEntry(psiElement.parent as ArendPattern, refactoringContext, defName)).first
                        is ArendLocalCoClause -> doProcessEntry(LocalCoClauseEntry(psiElement, refactoringContext, defName)).first
                        else -> null
                    }?.let { result ->
                        val elementToReplace = if (psiElement is ArendPattern) psiElement.parent else psiElement
                        textReplacements[elementToReplace] = result
                    }
                }

                for (replacementEntry in textReplacements) {
                    val file = replacementEntry.key.containingFile
                    val comparator = Comparator<Pair<TextRange, String>> { o1, o2 ->
                        val i = o1.first.startOffset - o2.first.startOffset
                        if (i > 0) 1 else if (i < 0) -1 else 0
                    }
                    val changesList = fileChangeMap.computeIfAbsent(file) { SortedList<Pair<TextRange, String>>(comparator) }
                    changesList.add(Pair(replacementEntry.key.textRange, replacementEntry.value))
                }

                for (changeEntry in fileChangeMap) { // Leave only non-overlapping top-level text changes
                    var sampleEntry: Pair<TextRange, String>? = null
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

        if (failingAppExprs.size > 0 && Messages.showYesNoDialog(
                ArendBundle.message("arend.coClause.changeArgumentExplicitness.question1", failingAppExprs.first().second.name ?: "?"),
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
                        textFile.replaceString(textRange.startOffset, textRange.endOffset, replacementEntry.second)
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

        return tele.replaceWithNotification(newTele)
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
        return element.parentOfType<ArendPattern>() ?: element.parentOfType<ArendArgumentAppExpr>() ?: element
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
    val isExplicit = (tele.text.first() == '(')
    val params = getTele(tele)?.joinToString(" ") { it.text }

    return when (tele) {
        is ArendNameTele -> {
            val type = tele.type!!.text
            factory.createNameTele(params, type, !isExplicit)
        }

        is ArendFieldTele -> {
            val type = tele.type!!.text
            factory.createFieldTele(params, type, !isExplicit)
        }

        is ArendTypeTele -> {
            val typedExpr = tele.typedExpr!!
            val expr = typedExpr.type
            if (expr == null) {
                factory.createTypeTele(null, typedExpr.text, !isExplicit)
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
 * Returns all references of `def` in `element`-scope
 */
private fun searchRefsInPsiElement(def: PsiElement, element: PsiElement): List<PsiReference> {
    val scope = LocalSearchScope(element)
    return ReferencesSearch.search(def, scope).findAll().toList()
}

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
