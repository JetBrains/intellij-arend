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
import com.intellij.util.containers.SortedList
import org.arend.codeInsight.ArendParameterInfoHandler.Companion.getAllParametersForReferable
import org.arend.error.CountingErrorReporter
import org.arend.error.DummyErrorReporter
import org.arend.ext.error.GeneralError
import org.arend.ext.variable.Variable
import org.arend.ext.variable.VariableImpl
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.Referable
import org.arend.naming.renamer.StringRenamer
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.PsiReferable
import org.arend.quickfix.referenceResolve.ResolveReferenceAction
import org.arend.refactoring.*
import org.arend.resolving.ArendReferableConverter
import org.arend.term.abs.Abstract
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.PrettyPrintVisitor
import org.arend.util.ArendBundle
import org.arend.util.appExprToConcrete
import org.arend.util.getBounds
import java.util.Collections.singletonList
import kotlin.math.max

data class ProcessAppExprResult(val text: String, val strippedText: String?, val parenthesizedPrefixTest: String?, val isAtomic: Boolean)
data class ArgumentDescriptor(val text: String, val strippedText: String?, val parenthesizedPrefixText: String?, val isExplicit: Boolean, val isAtomic: Boolean, val spacingText: String?) {
   constructor (processResult: ProcessAppExprResult, isExplicit: Boolean, spaceText: String?) : this(processResult.text, processResult.strippedText, processResult.parenthesizedPrefixTest, isExplicit, processResult.isAtomic, spaceText)
}

abstract class UsageEntry(val refactoringContext: RefactoringContext, val contextPsi: ArendCompositeElement) {
    abstract fun getArguments(): List<ArgumentDescriptor>
    open fun getLambdaParams(startFromIndex: Int): List<Parameter> = refactoringContext.oldParameters.subList(startFromIndex, max(startFromIndex, refactoringContext.oldParameters.size - refactoringContext.diffIndex))
    abstract fun getParameters(): Pair<List<Parameter>, List<NewParameter>>

    open fun getUnmodifiablePrefix(): String? = null
    open fun getUnmodifiableSuffix(): String? = null
}

const val FUNCTION_INDEX = -1

open class AppExpressionEntry(val expr: Concrete.AppExpression, val argumentAppExpr: ArendArgumentAppExpr, private val defName: String?, refactoringContext: RefactoringContext): UsageEntry(refactoringContext, argumentAppExpr) {
    private val exprPsi: List<PsiElement>? = refactoringContext.rangeData[expr]?.let { range -> argumentAppExpr.childrenWithLeaves.toList().filter { range.contains(it.textRange) }}
    private val procArguments = ArrayList<ArgumentDescriptor>()
    val procFunction: String
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
            procArguments.add(ArgumentDescriptor(processAppExpr(argument.expression, argumentAppExpr, defName, refactoringContext), argument.isExplicit, spacingMap[index]))
    }

    override fun getArguments(): List<ArgumentDescriptor> = procArguments

    override fun getParameters(): Pair<List<Parameter>, List<NewParameter>> = Pair(refactoringContext.oldParameters, refactoringContext.newParameters)
}

class NoArgumentsEntry(refExpr: Concrete.ReferenceExpression, refactoringContext: RefactoringContext): UsageEntry(refactoringContext, refExpr.data as ArendCompositeElement) {
    override fun getArguments(): List<ArgumentDescriptor> = emptyList()

    override fun getParameters(): Pair<List<Parameter>, List<NewParameter>> = Pair(refactoringContext.oldParameters, refactoringContext.newParameters)
}

class PatternEntry(pattern: ArendPattern, refactoringContext: RefactoringContext): UsageEntry(refactoringContext, pattern) {
    private val procArguments = ArrayList<ArgumentDescriptor>()
    init {
        val spacingMap = HashMap<Abstract.Pattern, String>()
        var buffer = ""
        for (block in pattern.childrenWithLeaves) {
            if (block is Abstract.Pattern) {
                spacingMap[block] = buffer
                buffer = ""
            } else if (!pattern.children.contains(block)) buffer += block.text
        }

        for (arg in pattern.arguments) {
            val data = arg.data as PsiElement
            var data1: PsiElement? = data
            while (true) {
                if (data1 is ArendAtomPatternOrPrefix && data1.atomPattern != null && data1.defIdentifier == null && data1.longName == null) {
                    data1 = data1.atomPattern; continue
                } else if (data1 is ArendAtomPattern && data1.patternList.size == 1 && (data1.lparen != null || data1.lbrace != null)) {
                    data1 = data1.patternList[0]; continue
                }
                break
            }
            val text = textGetter(data, refactoringContext.textReplacements)
            val strippedText = if (data1 != null) textGetter(data1, refactoringContext.textReplacements) else null
            val isAtomic = data1 is ArendDefIdentifier || data1 is ArendAtomPattern && (data1.patternList.size == 0 && data1.lparen != null || data1.underscore != null)
            procArguments.add(ArgumentDescriptor(text, strippedText, null, arg.isExplicit, isAtomic, spacingMap[arg]))
        }
    }

    override fun getLambdaParams(startFromIndex: Int): List<Parameter> = emptyList()

    override fun getArguments(): List<ArgumentDescriptor> = procArguments

    override fun getParameters(): Pair<List<Parameter>, List<NewParameter>> = Pair(refactoringContext.oldParametersConstructor!!, refactoringContext.newParametersConstructor!!)
}

class LocalCoClauseEntry(private val localCoClause: ArendLocalCoClause, refactoringContext: RefactoringContext): UsageEntry(refactoringContext, localCoClause) {
    private val procArguments = ArrayList<ArgumentDescriptor>()
    init {
        val spacingMap = HashMap<ArendLamParam, String>()
        var buffer = ""
        for (block in localCoClause.childrenWithLeaves) {
            if (block is ArendLamParam) {
                spacingMap[block] = buffer
                buffer = ""
            } else if (!localCoClause.children.contains(block) &&
                block.elementType != ArendElementTypes.PIPE &&
                block.prevSibling?.let{ it.elementType == ArendElementTypes.PIPE} != true) buffer += block.text
        }

        for (arg in localCoClause.lamParamList) {
            val data = arg.data as PsiElement
            var data1: PsiElement? = data
            while (true) {
                if (data1 is ArendAtomPattern && data1.patternList.size == 1 && (data1.lparen != null || data1.lbrace != null)) {
                    data1 = data1.patternList[0]; continue
                } else if (data1 is ArendLamTele && data1.identifierOrUnknownList.size == 1 && data1.expr == null) {
                    data1 = data1.identifierOrUnknownList[0]; continue
                }
                break
            }
            val text = textGetter(data, refactoringContext.textReplacements)
            val strippedText = if (data1 != null) textGetter(data1, refactoringContext.textReplacements) else null
            val isExplicit = when (data) {
                is ArendLamTele -> data.isExplicit
                is ArendAtomPattern -> data.isExplicit
                else -> data.text.trim().startsWith("{")
            }
            procArguments.add(ArgumentDescriptor(text, strippedText, null, isExplicit, data1 is ArendIdentifierOrUnknown, spacingMap[arg]))
        }
    }
    override fun getLambdaParams(startFromIndex: Int): List<Parameter> = emptyList()

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
}

data class Parameter(val isExplicit: Boolean, val referable: Referable?)
data class NewParameter(val isExplicit: Boolean, val oldParameter: Parameter?)
data class RefactoringContext(
    val def: PsiElement,
    val oldParameters: List<Parameter>, val newParameters: List<NewParameter>,
    val oldParametersConstructor: List<Parameter>?, val newParametersConstructor: List<NewParameter>?, /* These 2 fields make sense only for ArendConstructor */
    val textReplacements: HashMap<PsiElement, String>,
    val rangeData: HashMap<Concrete.Expression, TextRange>) {
    val diffIndex = oldParameters.reversed().zip(newParameters.reversed()).indexOfFirst { (o, n) -> n.oldParameter != o || n.isExplicit != o.isExplicit }
}

fun doProcessEntry(entry: UsageEntry, defName: String?, isInfix: Boolean = false): Pair<String /* default version */, String /* version with parenthesized operator */ > {
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

    entry.getLambdaParams(i).map {
        val freshName = StringRenamer().generateFreshName(VariableImpl(it.referable?.refName), context + referables)
        referables.add(VariableImpl(freshName))
        lambdaArgs += if (it.isExplicit) " $freshName" else " {$freshName}"
        oldArgToLambdaArgMap[it] = freshName
    }

    if (lambdaArgs != "") append("\\lam$lambdaArgs => ")
    for (e in oldArgToLambdaArgMap) parameterMap[e.key] = ArgumentDescriptor(e.value, null, null, isExplicit = true, isAtomic = true, spacingText = null)

    defaultBuilder.append(defName); parenthesizedPrefixBuilder.append(if (isInfix) "($defName)" else defName)

    var implicitArgPrefix = ""
    var spacingContents = ""
    for ((newParamIndex, newParam) in newParameters.withIndex()) {
        val oldParam = newParam.oldParameter
        if (oldParameters.indexOf(oldParam) >= i && newParamIndex >= newParameters.size - entry.refactoringContext.diffIndex) break

        val text = (oldParam?.let{ parameterMap[it]?.let{ d -> Pair(d.strippedText ?: d.text, !d.isAtomic) } ?: Pair("_", false) } ?: Pair("{?}", false)).let {
            if (newParam.isExplicit) (if (it.second) "(${it.first})" else it.first ) else (if (it.first.startsWith("-")) "{ ${it.first}}" else "{${it.first}}")
        }
        val spacing = oldParam?.let{ parameterMap[it]?.spacingText } ?: " "

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

    while (j < entry.getArguments().size) {
        append(" ${entry.getArguments()[j].text}")
        j++
    }

    entry.getUnmodifiableSuffix()?.let {
        append(it)
    }
    return Pair(defaultBuilder.toString(), parenthesizedPrefixBuilder.toString())
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
            is ArendAtomFieldsAcc -> if (exprData1.fieldAccList.size == 0) {
                exprData = exprData1.atom; continue
            }
            is ArendTuple -> if (exprData1.tupleExprList.size == 1) {
                exprData = exprData1.tupleExprList[0]; continue
            }
            is ArendTupleExpr -> if (exprData1.exprList.size == 1) {
                exprData = exprData1.exprList[0]; continue
            }
            is ArendAtom -> {
                val tuple = exprData1.tuple
                val literal = exprData1.literal
                if (tuple != null) {
                    exprData = tuple; continue
                } else if (literal != null) {
                    exprData = literal; continue
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
    return Pair(exprData, exprData is ArendAtom || exprData is ArendLongName || exprData is ArendTuple || exprData is ArendLiteral || exprData.text == "_")
}

fun processAppExpr(expr: Concrete.Expression, psiElement: ArendArgumentAppExpr, defName: String?, refactoringContext: RefactoringContext): ProcessAppExprResult {
    if (expr is Concrete.AppExpression) {
        val function = expr.function.data as PsiElement
        val resolve = tryResolveFunctionName(function)
        val resolveIsInfix = resolve is GlobalReferable && resolve.precedence.isInfix
        val appExprEntry = AppExpressionEntry(expr, psiElement, defName, refactoringContext)

        return if (refactoringContext.def == resolve) {
            val processResult = doProcessEntry(appExprEntry, defName, resolveIsInfix)
            ProcessAppExprResult(processResult.first, null, processResult.second,false)
        } else {
            val builder = StringBuilder()
            val parenthesizedBuilder = StringBuilder()
            fun append(text: String) { builder.append(text); parenthesizedBuilder.append(text) }

            val processedFunction = appExprEntry.procFunction
            var explicitArgCount = 0
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
            val isAtomic = if (appExprEntry.blocks.size == 1 && appExprEntry.blocks[0] == FUNCTION_INDEX) getStrippedPsi(expr.function.data as PsiElement).second else false
            ProcessAppExprResult(builder.toString(), null, parenthesizedBuilder.toString(), isAtomic)
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
                    override fun getLambdaParams(startFromIndex: Int): List<Parameter> = (this.getParameters().first.firstOrNull { it.isExplicit }?.let { listOf(it) } ?: emptyList()) + super.getLambdaParams(startFromIndex)

                    override fun getArguments(): List<ArgumentDescriptor> = listOf(ArgumentDescriptor("", null,  null, isExplicit = true, isAtomic = true, spacingText = null)) + super.getArguments()
                }, defName).first)
                return ProcessAppExprResult(builder.toString(), null, null, false)
            }
        }
        builder.append(processAppExpr(exprBody, psiElement, defName, refactoringContext).text)
        return ProcessAppExprResult(builder.toString(), null, null,false)
    } else if (expr is Concrete.ReferenceExpression) {
        if (expr.referent == refactoringContext.def) {
            val text = doProcessEntry(NoArgumentsEntry(expr, refactoringContext), defName).first
            return ProcessAppExprResult("(${text})", text, null, true)
        } else if ((expr.referent as? GlobalReferable)?.precedence?.isInfix == true) {
            val text = textGetter(expr.data as PsiElement, refactoringContext.textReplacements)
            return ProcessAppExprResult("(${text})", null, null, true)
        }
    }

    val (exprData, isAtomic) = getStrippedPsi(expr.data as PsiElement)

    return ProcessAppExprResult(textGetter(expr.data as PsiElement, refactoringContext.textReplacements),
        textGetter(exprData, refactoringContext.textReplacements), null, isAtomic)
}

fun textGetter(psi: PsiElement, textReplacements: HashMap<PsiElement, String>): String = textReplacements[psi] ?: buildString {
    if (psi.firstChild == null) append(psi.text) else
        for (c in psi.childrenWithLeaves)
            append(textGetter(c, textReplacements))
}

class ChangeArgumentExplicitnessIntention : SelfTargetingIntention<ArendCompositeElement>(
    ArendCompositeElement::class.java,
    ArendBundle.message("arend.coClause.changeArgumentExplicitness")
) {
    override fun isApplicableTo(element: ArendCompositeElement, caretOffset: Int, editor: Editor): Boolean {
        if (DumbService.isDumb(element.project)) return false

        return when (element) {
            is ArendNameTele, is ArendFieldTele, is ArendTypeTele ->
                element.parent?.let{ it is ArendDefinition || it is ArendClassField || it is ArendConstructor } ?: false
            else -> false
        }
    }

    override fun applyTo(element: ArendCompositeElement, project: Project, editor: Editor) {
        val elementOnCaret = element.containingFile.findElementAt(editor.caretModel.offset)
        val switchedArgIndexInTele = getSwitchedArgIndex(element, elementOnCaret)
        val def = element.ancestor() as? PsiReferable ?: return
        val teleIndexInDef = getTeleIndexInDef(def, element)

        // switch all variables in telescope

        if (switchedArgIndexInTele == null || switchedArgIndexInTele == -1) {
            //Change many variables
            val teleSize = if (element is ArendTypeTele && element.typedExpr?.identifierOrUnknownList?.isEmpty() == true) 1 else getTele(element)?.size ?: return

            chooseApplier(element)?.applyTo(def, (0 until teleSize).map { it + teleIndexInDef }.toSet())

            runWriteAction {
                switchTeleExplicitness(element)
            }
        } else {
            chooseApplier(element)?.applyTo(def, singletonList(switchedArgIndexInTele + teleIndexInDef).toSet())

            runWriteAction {
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

        val fCalls = ReferencesSearch.search(def).map { it.element }.filter {
            when (it) {
                is ArendRefIdentifier -> (it.parent as? ArendLongName)?.let { longName -> longName.refIdentifierList.lastOrNull() == it } ?: true
                else -> true
            }
        }
        val fCallParents = fCalls.map { getParentPsiFunctionCall(it)}.sortedBy { it.textLength }.toCollection(LinkedHashSet())
        val concreteSet = LinkedHashSet<Pair<PsiElement, Concrete.Expression?>>()
        val textReplacements = HashMap<PsiElement, String>()
        val fileChangeMap = HashMap<PsiFile, SortedList<Pair<TextRange, String>>>()
        val rangeData = HashMap<Concrete.Expression, TextRange>()
        val refactoringContext = RefactoringContext(def, oldParameters, newParameters, oldParametersConstructor, newParametersConstructor, textReplacements, rangeData)
        val refactoringTitle = ArendBundle.message("arend.coClause.changeArgumentExplicitness")
        val failingAppExprs = HashSet<ArendArgumentAppExpr>()

        if (!ProgressManager.getInstance().runProcessWithProgressSynchronously({
            runReadAction {
                val progressIndicator = ProgressManager.getInstance().progressIndicator
                for ((index, fCall) in fCallParents.withIndex()) {
                    progressIndicator.fraction = index.toDouble() / fCallParents.size
                    progressIndicator.checkCanceled()
                    when (fCall) {
                        is ArendArgumentAppExpr -> {
                            val errorReporter = CountingErrorReporter(GeneralError.Level.ERROR, DummyErrorReporter.INSTANCE)
                            appExprToConcrete(fCall, false, errorReporter)?.let{
                                concreteSet.add(Pair(fCall, it))
                            }
                            if (errorReporter.errorsNumber > 0) failingAppExprs.add(fCall)
                        }
                        is ArendPattern, is ArendLocalCoClause -> concreteSet.add(Pair(fCall, null))
                    }
                }

                for ((index, callEntry) in concreteSet.withIndex()) {
                    progressIndicator.fraction = index.toDouble() / concreteSet.size
                    progressIndicator.checkCanceled()
                    val psiElement = callEntry.first
                    val defName = if (def is PsiLocatedReferable && psiElement is ArendCompositeElement) ResolveReferenceAction.getTargetName(def, psiElement) else def.refName

                    when (psiElement) {
                        is ArendArgumentAppExpr -> {
                            try {
                                val expr = callEntry.second!!
                                refactoringContext.rangeData.clear()
                                getBounds(expr, psiElement.node.getChildren(null).toList(), rangeData)
                                processAppExpr(expr, psiElement, defName, refactoringContext).text
                            } catch (e: IllegalStateException) { // TODO: We could use custom exception in processAppExpr
                                failingAppExprs.add(psiElement)
                                null
                            }
                        }
                        is ArendPattern -> doProcessEntry(PatternEntry(psiElement, refactoringContext), defName).first
                        is ArendLocalCoClause -> doProcessEntry(LocalCoClauseEntry(psiElement, refactoringContext), defName).first
                        else -> null
                    }?.let { result ->
                        textReplacements[psiElement] = result
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
                ArendBundle.message("arend.coClause.changeArgumentExplicitness.question1", def.name ?: "?"),
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
     * Extracts reference to the function of given call
     */
    abstract fun extractRefIdFromCalling(def: PsiElement, call: PsiElement): PsiReference?

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
    override fun getParentPsiFunctionCall(element: PsiElement): PsiElement =
        if (element.parent is ArendPattern || element.parent is ArendLongName && element.parent.parent is ArendPattern) element.ancestor<ArendPattern>()!! else
        element.ancestor<ArendArgumentAppExpr>() ?: element

    override fun getCallingParameters(call: PsiElement): List<PsiElement> = when (call) {
        is ArendArgumentAppExpr -> call.argumentList
        is ArendPattern -> call.atomPatternOrPrefixList
        else -> throw IllegalArgumentException()
    }

    override fun getContext(element: PsiElement): List<Variable> {
        val argumentAppExpr = element as ArendArgumentAppExpr
        return argumentAppExpr.scope.elements.map { VariableImpl(it.textRepresentation()) }
    }

    override fun extractRefIdFromCalling(def: PsiElement, call: PsiElement): PsiReference? {
        val function = when (call) {
            is ArendArgumentAppExpr -> call.atomFieldsAcc
            is ArendPattern -> when {
                call.defIdentifier != null -> call.defIdentifier
                call.longName != null -> call.longName?.refIdentifierList?.last()
                else -> null
            }
            else -> null
        } ?: throw IllegalArgumentException()

        val refs = searchRefsInPsiElement(def, function)
        return if (refs.isEmpty()) null else refs.first()
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
            is Abstract.Expression -> {
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

    override fun extractRefIdFromCalling(def: PsiElement, call: PsiElement): PsiReference? {
        val function = (call as ArendLocalCoClause).longName!!
        val refs = searchRefsInPsiElement(def, function)
        return if (refs.isEmpty()) null else refs.first()
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
            val type = tele.expr!!.text
            factory.createNameTele(params, type, !isExplicit)
        }

        is ArendFieldTele -> {
            val type = tele.expr!!.text
            factory.createFieldTele(params, type, !isExplicit)
        }

        is ArendTypeTele -> {
            val typedExpr = tele.typedExpr!!
            val expr = typedExpr.expr
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
