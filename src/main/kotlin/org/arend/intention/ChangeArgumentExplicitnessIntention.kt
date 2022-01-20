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
import com.intellij.util.containers.SortedList
import org.arend.codeInsight.ArendParameterInfoHandler.Companion.getAllParametersForReferable
import org.arend.error.CountingErrorReporter
import org.arend.error.DummyErrorReporter
import org.arend.ext.error.GeneralError
import org.arend.ext.variable.Variable
import org.arend.ext.variable.VariableImpl
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

/*
    The key steps of this intention:
      * Wraps into parens all calls which contains function with changed argument. It helps not to go up very high in the term.
        Firstly, we try to wrap the most inner calls and then the top

      * Rewrite all usages. Again, we try to rewrite the most inner call and then the top.
        It saves from the inner psi-element becoming invalid.

      * Rewrite definition

      * If many arguments were switched, the steps are repeated
 */
data class ArgumentDescriptor(val isExplicit: Boolean, val isInsideParentheses: Boolean, val isEmptyPattern: Boolean)

abstract class UsageEntry(val refactoringContext: RefactoringContext, val contextPsi: ArendCompositeElement) {
    abstract fun getArguments(): List<ArgumentDescriptor>
    abstract fun getProcessedArguments(): List<String>
    open fun getLambdaParams(startFromIndex: Int): List<Parameter> = refactoringContext.oldParameters.subList(startFromIndex, max(startFromIndex, refactoringContext.oldParameters.size - refactoringContext.diffIndex))
    abstract fun getParameters(): Pair<List<Parameter>, List<NewParameter>>

    open fun getUnmodifiablePrefix(): String? = null
    open fun getUnmodifiableSuffix(): String? = null
}

const val FUNCTION_INDEX = -1

open class AppExpressionEntry(val expr: Concrete.AppExpression, val argumentAppExpr: ArendArgumentAppExpr, private val defName: String?, refactoringContext: RefactoringContext): UsageEntry(refactoringContext, argumentAppExpr) {
    private val exprPsi: List<PsiElement>? = refactoringContext.rangeData[expr]?.let { range -> argumentAppExpr.childrenWithLeaves.toList().filter { range.contains(it.textRange) }}
    private val procArguments = ArrayList<String>()
    val procFunction: String
    val blocks: ArrayList<Any>

    init {
        if (exprPsi == null) throw IllegalStateException()
        blocks = ArrayList(exprPsi)
        fun getBlocksInRange (range: TextRange): List<PsiElement> = blocks.filterIsInstance<PsiElement>().filter { range.contains(it.textRange) }
        val replacementMap = HashMap<Pair<PsiElement, PsiElement>, Int>()

        val functionBlock = getBlocksInRange(refactoringContext.rangeData[expr.function]!!).let{ if (it.size != 1)
            throw IllegalStateException() else it.first()}
        replacementMap[Pair(functionBlock, functionBlock)] = FUNCTION_INDEX

        for (argument in expr.arguments) refactoringContext.rangeData[argument.expression]?.let { argRange ->
            procArguments.add(processAppExpr(argument.expression, argumentAppExpr, defName, refactoringContext))
            replacementMap[getBlocksInRange(argRange).let{ Pair(it.first(), it.last()) }] = procArguments.size - 1
        }

        procFunction = textGetter(expr.function.data as PsiElement, refactoringContext.textReplacements)
        for (e in replacementMap) {
            val sI = blocks.indexOf(e.key.first)
            val eI = blocks.indexOf(e.key.second)
            if (sI == -1 || eI == -1) throw IllegalStateException()
            blocks.removeAll(blocks.subList(sI, eI + 1).toSet())
            blocks.add(sI, e.value)
        }
    }

    override fun getProcessedArguments(): List<String> = procArguments

    override fun getArguments(): List<ArgumentDescriptor> = expr.arguments.map {
        k -> ArgumentDescriptor(k.isExplicit, k.expression.toString().trim().startsWith("("), false)
    }

    override fun getParameters(): Pair<List<Parameter>, List<NewParameter>> = Pair(refactoringContext.oldParameters, refactoringContext.newParameters)
}

class NoArgumentsEntry(refExpr: Concrete.ReferenceExpression, refactoringContext: RefactoringContext): UsageEntry(refactoringContext, refExpr.data as ArendCompositeElement) {
    override fun getArguments(): List<ArgumentDescriptor> = emptyList()

    override fun getProcessedArguments(): List<String> = emptyList()

    override fun getParameters(): Pair<List<Parameter>, List<NewParameter>> = Pair(refactoringContext.oldParameters, refactoringContext.newParameters)
}

class PatternEntry(private val pattern: ArendPattern, refactoringContext: RefactoringContext): UsageEntry(refactoringContext, pattern) {
    override fun getLambdaParams(startFromIndex: Int): List<Parameter> = emptyList()

    override fun getArguments(): List<ArgumentDescriptor> = pattern.arguments.map {
        k -> ArgumentDescriptor(k.isExplicit, (k.data as PsiElement).text.trim().startsWith("("), (k is ArendAtomPatternOrPrefix) && (k.text == "()"))
    }

    override fun getProcessedArguments(): List<String> = pattern.arguments.map {
        textGetter(it.data as PsiElement, refactoringContext.textReplacements)
    }

    override fun getParameters(): Pair<List<Parameter>, List<NewParameter>> = Pair(refactoringContext.oldParametersConstructor!!, refactoringContext.newParametersConstructor!!)
}

class LocalCoClauseEntry(private val localCoClause: ArendLocalCoClause, refactoringContext: RefactoringContext): UsageEntry(refactoringContext, localCoClause) {
    override fun getLambdaParams(startFromIndex: Int): List<Parameter> = emptyList()

    override fun getArguments(): List<ArgumentDescriptor> = localCoClause.lamParamList.map {
        k -> ArgumentDescriptor(!(k as PsiElement).text.startsWith("{"), (k.data as PsiElement).text.trim().startsWith("("), false)
    }

    override fun getProcessedArguments(): List<String> = localCoClause.lamParamList.map {
        textGetter(it.data as PsiElement, refactoringContext.textReplacements)
    }

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

fun doProcessEntry(entry: UsageEntry, defName: String?): String = buildString {
    val processedArguments = entry.getProcessedArguments()
    val (oldParameters, newParameters) = entry.getParameters()

    entry.getUnmodifiablePrefix()?.let { append(it) }

    var i = 0
    var j = 0
    val parameterMap = HashMap<Parameter, Pair<String, Boolean>?>()
    while (i < oldParameters.size && j < entry.getArguments().size) {
        val param = oldParameters[i]
        val arg = entry.getArguments()[j]
        val argExplicit = arg.isExplicit
        if (argExplicit == param.isExplicit) {
            parameterMap[param] = processedArguments[j].trim().let{
                Pair(if (it.startsWith("{") && it.endsWith("}") && !argExplicit ||
                    it.startsWith("(") && it.endsWith(")") && !arg.isEmptyPattern) it.substring(1, it.length-1) else it,
                    it.trim().startsWith("(") || it.contains(" ") || arg.isInsideParentheses)
            }
            i++
            j++
        } else if (!param.isExplicit && argExplicit) {
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
    for (e in oldArgToLambdaArgMap) parameterMap[e.key] = Pair(e.value, false)

    append("$defName")
    var implicitArgPrefix = ""
    for ((newParamIndex, newParam) in newParameters.withIndex()) {
        val oldParam = newParam.oldParameter
        if (oldParameters.indexOf(oldParam) >= i && newParamIndex >= newParameters.size - entry.refactoringContext.diffIndex) break
        val text = (oldParam?.let{ parameterMap[it] ?: Pair("_", false) } ?: Pair("{?}", false)).let { if (newParam.isExplicit) (if (it.second) "(${it.first})" else it.first ) else (if (it.first.startsWith("-")) "{ ${it.first}}" else "{${it.first}}") }
        if (text == "{_}") implicitArgPrefix += " $text" else {
            if (!newParam.isExplicit) {
                append(implicitArgPrefix)
            }
            append(" $text")
            implicitArgPrefix = ""
        }
    }

    while (j < entry.getArguments().size) {
        append(" ${processedArguments[j]}")
        j++
    }

    entry.getUnmodifiableSuffix()?.let {
        append(it)
    }
}

fun processAppExpr(expr: Concrete.Expression, psiElement: ArendArgumentAppExpr, defName: String?, refactoringContext: RefactoringContext): String = buildString {
    if (expr is Concrete.AppExpression) {
        val function = expr.function.data as PsiElement
        val resolve = tryResolveFunctionName(function)
        val appExprEntry = AppExpressionEntry(expr, psiElement, defName, refactoringContext)

        if (refactoringContext.def == resolve) {
            append(doProcessEntry(appExprEntry, defName))
            return@buildString
        } else {
            val (processedFunction, processedArguments) = Pair(appExprEntry.procFunction, appExprEntry.getProcessedArguments())

            for (block in appExprEntry.blocks) when (block) {
                is Int -> if (block == FUNCTION_INDEX) append(processedFunction) else append(processedArguments[block])
                is PsiElement -> append(textGetter(block, refactoringContext.textReplacements))
            }
            return@buildString
        }

    } else if (expr is Concrete.LamExpression && expr.data == null) {
        val exprBody = expr.body
        if (exprBody is Concrete.AppExpression) {
            val function = exprBody.function.data as PsiElement
            val resolve = tryResolveFunctionName(function)
            val isPostfix = (function as ArendIPName).postfix != null
            if (isPostfix && refactoringContext.def == resolve) {
                append(doProcessEntry(object: AppExpressionEntry(exprBody, psiElement, defName, refactoringContext) {
                    override fun getLambdaParams(startFromIndex: Int): List<Parameter> = (this.getParameters().first.firstOrNull { it.isExplicit }?.let { listOf(it) } ?: emptyList()) + super.getLambdaParams(startFromIndex)

                    override fun getProcessedArguments(): List<String> = listOf("") + super.getProcessedArguments()
                }, defName))
                return@buildString
            }
        }
        append(processAppExpr(exprBody, psiElement, defName, refactoringContext))
        return@buildString
    } else if (expr is Concrete.ReferenceExpression) {
        if (expr.referent == refactoringContext.def) {
            append("(${doProcessEntry(NoArgumentsEntry(expr, refactoringContext), defName)})")
            return@buildString
        }
    }

    val exprData = expr.data
    if (exprData is PsiElement) append(textGetter(exprData, refactoringContext.textReplacements))
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
                                processAppExpr(expr, psiElement, defName, refactoringContext)
                            } catch (e: IllegalStateException) { // TODO: We could use custom exception in processAppExpr
                                failingAppExprs.add(psiElement)
                                null
                            }
                        }
                        is ArendPattern -> doProcessEntry(PatternEntry(psiElement, refactoringContext), defName)
                        is ArendLocalCoClause -> doProcessEntry(LocalCoClauseEntry(psiElement, refactoringContext), defName)
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
