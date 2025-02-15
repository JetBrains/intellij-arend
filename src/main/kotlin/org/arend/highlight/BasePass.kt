package org.arend.highlight

import com.intellij.codeInsight.daemon.impl.*
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.Strings
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.startOffset
import com.intellij.psi.util.validOrNull
import com.intellij.util.containers.mapSmartNotNull
import com.intellij.xml.util.XmlStringUtil
import org.arend.IArendFile
import org.arend.codeInsight.ArendCodeInsightUtils.Companion.getAllParametersForReferable
import org.arend.codeInsight.completion.STATEMENT_WT_KWS_TOKENS
import org.arend.codeInsight.completion.withAncestors
import org.arend.core.context.param.DependentLink
import org.arend.core.context.param.EmptyDependentLink
import org.arend.core.expr.ReferenceExpression
import org.arend.error.ParsingError
import org.arend.error.ParsingError.Kind.*
import org.arend.ext.concrete.ConcreteSourceNode
import org.arend.ext.concrete.definition.FunctionKind
import org.arend.ext.error.*
import org.arend.ext.error.quickFix.ErrorQuickFix
import org.arend.ext.prettyprinting.PrettyPrinterFlag
import org.arend.ext.prettyprinting.doc.DocFactory.vHang
import org.arend.ext.prettyprinting.doc.DocStringBuilder
import org.arend.ext.reference.DataContainer
import org.arend.naming.error.DuplicateOpenedNameError
import org.arend.naming.error.ExistingOpenedNameError
import org.arend.naming.error.NotInScopeError
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.Referable
import org.arend.naming.scope.EmptyScope
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.ext.*
import org.arend.psi.ext.ReferableBase
import org.arend.quickfix.*
import org.arend.quickfix.implementCoClause.CoClausesKey
import org.arend.quickfix.implementCoClause.ImplementFieldsQuickFix
import org.arend.quickfix.implementCoClause.makeFieldList
import org.arend.quickfix.instance.AddInstanceArgumentQuickFix
import org.arend.quickfix.instance.InstanceInferenceQuickFix
import org.arend.quickfix.instance.ReplaceWithLocalInstanceQuickFix
import org.arend.quickfix.referenceResolve.ArendImportHintAction
import org.arend.quickfix.removers.*
import org.arend.quickfix.replacers.*
import org.arend.refactoring.replaceExprSmart
import org.arend.term.abs.Abstract
import org.arend.term.abs.IncompleteExpressionError
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.PrettyPrinterConfigWithRenamer
import org.arend.typechecking.error.local.*
import org.arend.typechecking.error.local.CertainTypecheckingError.Kind.*
import org.arend.ext.error.InstanceInferenceError
import org.arend.naming.reference.TCDefReferable
import org.arend.psi.ArendExpressionCodeFragment
import org.arend.quickfix.instance.AddRecursiveInstanceArgumentQuickFix
import org.arend.typechecking.error.local.inference.FunctionArgInferenceError
import org.arend.typechecking.error.local.inference.LambdaInferenceError
import org.arend.typechecking.error.local.inference.RecursiveInstanceInferenceError
import org.arend.util.ArendBundle
import java.util.*

abstract class BasePass(protected open val file: IArendFile, editor: Editor, name: String, protected val textRange: TextRange)
    : ProgressableTextEditorHighlightingPass(file.project, editor.document, name, file, editor, textRange, false, null), ErrorReporter, HighlightingCollector {

    private val highlights = ArrayList<HighlightInfo>()
    private val errorList = ArrayList<GeneralError>()

    fun getHighlights() = highlights

    override fun applyInformationWithProgress() {
        ApplicationManager.getApplication().invokeLater({
            applyInformationLater()
        }, if (file is ArendExpressionCodeFragment) ModalityState.defaultModalityState() else ModalityState.stateForComponent(editor.component))
    }

    open fun applyInformationLater() {
        for (error in errorList) {
            val list = error.cause?.let { it as? Collection<*> ?: listOf(it) }?.mapSmartNotNull { getCauseElement(it)?.validOrNull() }
            if (list.isNullOrEmpty()) {
                val psi = ((error as? LocalError)?.definition as? DataContainer)?.data as? PsiElement
                if (psi != null) {
                    reportToEditor(error, psi)
                }
            } else {
                for (psi in list) {
                    reportToEditor(error, psi)
                }
            }
        }

        if (isValid) {
            UpdateHighlightersUtil.setHighlightersToEditor(myProject, document, textRange.startOffset, textRange.endOffset, highlights, colorsScheme, id)
        }
    }

    fun addHighlightInfo(builder: HighlightInfo.Builder) {
        val info = builder.create()
        if (info != null) {
            highlights.add(info)
        }
    }

    override fun addHighlightInfo(range: TextRange, colors: ArendHighlightingColors) {
        addHighlightInfo(HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION).range(range).textAttributes(colors.textAttributesKey))
    }

    private fun createHighlightInfoBuilder(error: GeneralError, range: TextRange, type: HighlightInfoType? = null): HighlightInfo.Builder {
        val ppConfig = PrettyPrinterConfigWithRenamer(EmptyScope.INSTANCE)
        ppConfig.expressionFlags = EnumSet.of(PrettyPrinterFlag.SHOW_LOCAL_FIELD_INSTANCE)
        return HighlightInfo.newHighlightInfo(type ?: levelToHighlightInfoType(error.level))
            .range(range)
            .severity(levelToSeverity(error.level))
            .description(
                run {
                    var message: String? = null
                    ApplicationManager.getApplication().run {
                        executeOnPooledThread {
                            runReadAction {
                                message = error.shortMessage
                            }
                        }.get()
                    }
                    message ?: ""
                }
            )
            .escapedToolTip(XmlStringUtil.escapeString(DocStringBuilder.build(vHang(error.getShortHeaderDoc(ppConfig), error.getBodyDoc(ppConfig)))).replace("\n", "<br>"))
    }

    fun registerFix(builder: HighlightInfo.Builder, fix: IntentionAction) {
        builder.registerFix(fix, null, fix.text, null, null)
    }

    fun reportToEditor(error: GeneralError, cause: PsiElement) {
        if (error is IncompleteExpressionError || file != cause.containingFile) {
            return
        }

        if (error is NotInScopeError) {
            val ref: ArendReferenceElement? = when (cause) {
                is ArendLongName -> cause.refIdentifierList.getOrNull(error.index)
                is ArendReferenceElement -> cause
                is ArendStatCmd -> cause.longName?.refIdentifierList?.getOrNull(error.index)
                else -> null
            }
            val builder = createHighlightInfoBuilder(error, ref?.textRange ?: getImprovedTextRange(error, cause), HighlightInfoType.WRONG_REF)
            if (ref != null && error.index == 0) {
                registerFix(builder, ArendImportHintAction(ref))
            }
            addHighlightInfo(builder)
            return
        }
        if (error is CannotFindConstructorError && cause is ArendPattern) {
            val singleRef = cause.singleReferable
            val refElement = cause.referenceElement
            val references = when {
                singleRef != null -> listOf(singleRef)
                refElement != null -> listOfNotNull(refElement.takeIf { it.longName.size == 1 }?.descendantOfType<ArendReferenceElement>())
                else -> cause.sequence.mapNotNull { subpattern -> subpattern.singleReferable ?: subpattern.referenceElement?.takeIf { it.longName.size == 1 }?.descendantOfType<ArendReferenceElement>() }
            }
            for (referenceElement in references) {
                val builder = createHighlightInfoBuilder(error, referenceElement.textRange, HighlightInfoType.WRONG_REF)
                val fix = ArendImportHintAction(referenceElement)
                registerFix(builder, fix)
                addHighlightInfo(builder)
            }
            return
        }
        val textRange = getImprovedTextRange(error, cause)
        val builder = createHighlightInfoBuilder(error, textRange)
        if (textRange.endOffset == textRange.startOffset + 1 && document.charsSequence[textRange.startOffset] == '\n') {
            builder.endOfLine()
        }

        when (error) {
            is GoalError -> {
                val incomplete = isIncomplete(cause)
                if (incomplete && cause !is LeafPsiElement) {
                    val next = cause.nextElement
                    if (next == null || next is PsiWhiteSpace && next.text.firstOrNull().let { it == '\n' || it == '\r' }) {
                        builder.endOfLine()
                    }
                }
                val coClauseBase = cause.ancestor<CoClauseBase>()
                val coClauseBaseFixData = coClauseBase?.getUserData(CoClausesKey)
                if (coClauseBaseFixData != null) registerFix(builder, object : ImplementFieldsQuickFix(SmartPointerManager.createPointer(coClauseBase), true, coClauseBaseFixData) {
                    override fun getText(): String = ArendBundle.getMessage("arend.coClause.replaceWithEmptyImplementation")
                })

                if (error.errors.all { it.level != GeneralError.Level.ERROR }) when {
                    error.goalSolver != null -> cause.ancestor<ArendExpr>()?.let {
                        val expr = when (it) {
                            is ArendLongNameExpr -> it.parent as? ArendArgumentAppExpr ?: it
                            is ArendLiteral -> (it.topmostEquivalentSourceNode as? ArendAtomFieldsAcc)?.parent as? ArendArgumentAppExpr
                                ?: it
                            else -> it
                        }
                        val action: (Editor, Concrete.Expression, String) -> Unit = { editor, concrete, text ->
                            if (incomplete) {
                                var offset = cause.textRange.endOffset
                                var reformat = false
                                if (cause is LeafPsiElement) {
                                    val next = cause.nextSibling
                                    if (next is PsiWhiteSpace) {
                                        val whitespaces = next.text
                                        val first = whitespaces.indexOf('\n')
                                        if (first != -1) {
                                            val second = whitespaces.indexOf('\n', first + 1)
                                            offset = next.textRange.startOffset + if (second == -1) first + 1 else second
                                            reformat = true
                                        }
                                    }
                                }
                                val prefix = when {
                                    cause is ArendLetExpr && cause.inKw == null -> " \\in "
                                    cause is ArendLamExpr && cause.fatArrow == null -> " => "
                                    cause is LeafPsiElement -> ""
                                    else -> " "
                                }
                                editor.document.insertString(offset, "$prefix$text")

                                if (reformat) {
                                    val file = cause.containingFile
                                    CodeStyleManager.getInstance(file.project).reformatText(file, offset, offset + prefix.length + text.length)
                                }
                            } else {
                                val exprRange = cause.textRange
                                val replacement =
                                    replaceExprSmart(editor.document, expr, null, exprRange, null, concrete, text)
                                if (text.contains("{?}")) {
                                    val goalOffset = editor.document.charsSequence.let { charSeq ->
                                        val start = exprRange.startOffset
                                        val end = exprRange.startOffset + replacement.length
                                        Strings.indexOf(charSeq, "{?}", start, end)
                                    }
                                    if (goalOffset != -1) {
                                        editor.caretModel.moveToOffset(goalOffset)
                                    }
                                }
                            }
                        }
                        registerFix(builder, GoalSolverFillingQuickFix(expr, error, action))
                        for (solver in error.additionalSolvers) {
                            registerFix(builder, InteractiveGoalSolverQuickFix(expr, error, solver, action))
                        }
                    }
                    cause is ArendGoal && cause.expr != null -> registerFix(builder, GoalFillingQuickFix(cause))
                }
            }
            is ParsingError -> when (error.kind) {
                MISPLACED_IMPORT -> {
                    val errorCause = error.cause
                    if (errorCause is ArendStatCmd && errorCause.isValid) {
                        registerFix(builder, MisplacedImportQuickFix(SmartPointerManager.createPointer(errorCause)))
                    }
                }
                CLASSIFYING_FIELD_IN_RECORD -> {
                    registerFix(builder, RemoveClassifyingFieldQuickFix(file.findElementAt(textRange.startOffset)))
                }
                else -> {
                }
            }

            is DuplicateOpenedNameError -> {
                val errorCause = error.cause
                if (errorCause is PsiElement && errorCause.isValid) {
                    registerFix(builder, RenameDuplicateNameQuickFix(SmartPointerManager.createPointer(errorCause), error.referable))
                    registerFix(builder, HideImportQuickFix(SmartPointerManager.createPointer(errorCause), error.referable))
                }
            }

            is ExistingOpenedNameError -> {
                val errorCause = error.cause
                if (errorCause is PsiElement && errorCause.isValid) {
                    registerFix(builder, RenameDuplicateNameQuickFix(SmartPointerManager.createPointer(errorCause), null))
                    registerFix(builder, HideImportQuickFix(SmartPointerManager.createPointer(errorCause), null))
                }
            }

            is FieldsImplementationError ->
                if (error.alreadyImplemented) {
                    val errorCause = error.cause.data
                    if (errorCause is ArendCoClause && errorCause.isValid) {
                        registerFix(builder, RemoveCoClauseQuickFix(SmartPointerManager.createPointer(errorCause)))
                    }
                } else {
                    val ref = error.classRef
                    val classRef = if (ref is Referable) ref.underlyingReferable else ref
                    if (classRef is ClassReferable) {
                        registerFix(builder, ImplementFieldsQuickFix(SmartPointerManager.createPointer(cause), false, makeFieldList(error.fields, classRef)))
                    }
                    if (cause is ArendNewExpr) {
                        cause.putUserData(CoClausesKey, null)
                    }
                }

            is MissingClausesError -> if (cause is ArendCompositeElement)
                registerFix(builder, ImplementMissingClausesQuickFix(error, SmartPointerManager.createPointer(cause)))

            is ExpectedConstructorError -> if (cause is ArendCompositeElement)
                registerFix(builder, ExpectedConstructorQuickFix(error, SmartPointerManager.createPointer(cause)))

            is ImpossibleEliminationError -> if (cause is ArendCompositeElement)
                registerFix(builder, ImpossibleEliminationQuickFix(error, SmartPointerManager.createPointer(cause)))

            is DataTypeNotEmptyError -> if (cause is ArendCompositeElement)
                registerFix(builder, ReplaceAbsurdPatternQuickFix(error.constructors, SmartPointerManager.createPointer(cause)))

            is CertainTypecheckingError -> when (error.kind) {
                TOO_MANY_PATTERNS, EXPECTED_EXPLICIT_PATTERN, IMPLICIT_PATTERN -> if (cause is Abstract.Pattern) {
                    val single = error.kind == EXPECTED_EXPLICIT_PATTERN
                    if (error.kind != TOO_MANY_PATTERNS) {
                        cause.let {
                            if (it.isValid) {
                                registerFix(builder, MakePatternExplicitQuickFix(SmartPointerManager.createPointer(it as ArendPattern), single))
                            }
                        }
                    }

                    if (!single || cause.nextSibling.findNextSibling { it is ArendPattern } != null) {
                        registerFix(builder, RemovePatternsQuickFix(SmartPointerManager.createPointer(cause as ArendPattern), single))
                    }
                }
                AS_PATTERN_IGNORED -> if (cause is ArendAsPattern) registerFix(builder, RemoveAsPatternQuickFix(SmartPointerManager.createPointer(cause)))
                BODY_IGNORED ->
                    cause.ancestor<ArendClause>()?.let {
                        if (it.isValid) {
                            registerFix(builder, RemovePatternRightHandSideQuickFix(SmartPointerManager.createPointer(it)))
                        }
                    }
                PATTERN_IGNORED -> if (cause is Abstract.Pattern) registerFix(builder, ReplaceWithWildcardPatternQuickFix(SmartPointerManager.createPointer(cause)))
                COULD_BE_LEMMA, AXIOM_WITH_BODY -> if (cause is ArendDefFunction) registerFix(builder, ReplaceFunctionKindQuickFix(SmartPointerManager.createPointer(cause.functionKw), FunctionKind.LEMMA))
                LEVEL_IGNORED -> registerFix(builder, RemoveLevelQuickFix(SmartPointerManager.createPointer(cause)))
                CASE_RESULT_TYPE -> registerFix(builder, AddReturnKeywordQuickFix(SmartPointerManager.createPointer(cause)))
                TRUNCATED_WITHOUT_UNIVERSE -> {
                    if (cause is ArendDefData) {
                        registerFix(builder, AddTruncatedUniverseQuickFix(SmartPointerManager.createPointer(cause)))
                        registerFix(builder, RemoveTruncatedKeywordQuickFix(SmartPointerManager.createPointer(cause)))
                    }
                }
                DATA_WONT_BE_TRUNCATED -> {
                    if (cause is ArendTruncatedUniverseAppExpr) {
                        registerFix(builder, RemoveTruncatedUniverseQuickFix(SmartPointerManager.createPointer(cause)))
                    }
                    var element: PsiElement? = cause
                    while (element != null && element !is ArendDefData) {
                        element = element.parent
                    }
                    if (element is ArendDefData) {
                        registerFix(builder, RemoveTruncatedKeywordQuickFix(SmartPointerManager.createPointer(element)))
                    }
                }
                NO_CLASSIFYING_IGNORED -> {
                    registerFix(builder, RemoveNoClassifyingKeywordQuickFix(cause.childOfType(NO_CLASSIFYING_KW)))
                }
                USELESS_LEVEL -> {
                    if (cause is ArendDefFunction) {
                        registerFix(builder, RemoveUseLevelQuickFix(SmartPointerManager.createPointer(cause)))
                    }
                }
                else -> {}
            }

            is LevelMismatchError -> when (cause) {
                is ArendDefFunction -> if (error.kind != LevelMismatchError.TargetKind.AXIOM)
                    registerFix(builder, ReplaceFunctionKindQuickFix(SmartPointerManager.createPointer(cause.functionKw), FunctionKind.FUNC))
                is ArendClassField -> {
                    val propKw = (cause.parent as? ArendClassStat)?.propertyKw
                    if (propKw != null) registerFix(builder, ReplaceFieldKindQuickFix(SmartPointerManager.createPointer(propKw)))
                }
                is ArendTypeTele, is ArendNameTele -> {
                    val propKw = when (cause) {
                        is ArendTypeTele -> cause.propertyKw
                        is ArendNameTele -> cause.propertyKw
                        else -> null
                    }
                    if (propKw != null) registerFix(builder, ReplaceSigmaFieldKindQuickFix(SmartPointerManager.createPointer(propKw)))
                }
                else -> {}
            }

            is RedundantClauseError -> if (cause is ArendClause) registerFix(builder, RemoveClauseQuickFix(SmartPointerManager.createPointer(cause)))

            is RedundantCoclauseError -> if (cause is ArendLocalCoClause) registerFix(builder, RemoveCoClauseQuickFix(SmartPointerManager.createPointer(cause)))

            is InstanceInferenceError -> if (cause is ArendLongName) {
                val classifyingExpression = error.classifyingExpression
                val isLocal = (classifyingExpression is ReferenceExpression) && DependentLink.Helper.toList((error.definition as? TCDefReferable)?.typechecked?.parameters ?: EmptyDependentLink.getInstance()).contains(classifyingExpression.binding)
                if (isLocal) registerFix(builder, ReplaceWithLocalInstanceQuickFix(error, SmartPointerManager.createPointer(cause))) else registerFix(builder, InstanceInferenceQuickFix(error, SmartPointerManager.createPointer(cause)))
                registerFix(builder, AddInstanceArgumentQuickFix(error, SmartPointerManager.createPointer(cause)))
                if (error is RecursiveInstanceInferenceError) {
                    registerFix(builder, AddRecursiveInstanceArgumentQuickFix(error, SmartPointerManager.createPointer(cause)))
                }
            }

            is DataUniverseError -> {
                registerFix(builder, DataUniverseQuickFix(SmartPointerManager.createPointer(cause), error))
            }

            is ArgumentExplicitnessError -> {
                val message = error.message
                if (message.contains("explicit")) {
                    registerFix(builder, ExplicitnessQuickFix(SmartPointerManager.createPointer(cause)))
                } else if (message.contains("implicit")) {
                    if (cause is ArendNameTele) {
                        registerFix(builder, ImplicitnessQuickFix(SmartPointerManager.createPointer(cause)))
                    }
                }
            }

            is LambdaInferenceError -> {
                registerFix(builder, LambdaInferenceQuickFix(SmartPointerManager.createPointer(cause), error))
            }

            is FunctionArgInferenceError -> {
                if (error.definition != null) {
                    val params = getAllParametersForReferable(error.definition.referable.underlyingReferable, cause as? ArendCompositeElement)?.first
                    if (params != null && error.index <= params.size)
                        registerFix(builder, FunctionArgInferenceQuickFix(SmartPointerManager.createPointer(cause), error))
                }
            }

            is ElimSubstError -> {
                registerFix(builder, ElimSubstQuickFix(SmartPointerManager.createPointer(cause), error))
            }

            is SquashedDataError -> {
                registerFix(builder, SquashedDataQuickFix(SmartPointerManager.createPointer(cause)))
            }

            is TruncatedDataError -> {
                if (((error.definition as? DataContainer)?.data as? PsiElement)?.descendantOfType<ArendReturnExpr>()?.firstChild?.text == "\\level") {
                    registerFix(builder, TruncatedDataQuickFix(SmartPointerManager.createPointer(cause), error))
                }
            }

            is FieldDependencyError -> {
                if (cause is ArendLocalCoClause) {
                    registerFix(builder, FieldDependencyQuickFix(SmartPointerManager.createPointer(cause), error))
                }
            }

            is ImplicitLambdaError -> {
                if (error.parameter != null) {
                    registerFix(builder, ImplicitLambdaQuickFix(SmartPointerManager.createPointer(cause), error))
                }
            }

            is MissingArgumentsError -> {
                registerFix(builder, AddMissingArgumentsQuickFix(error, SmartPointerManager.createPointer(cause)))
            }

            is IgnoredLevelsError -> {
                registerFix(builder, RemoveIgnoredLevelsQuickFix(SmartPointerManager.createPointer(cause)))
            }

            is TypecheckingError -> {
                if (error.message.contains("\\strict is ignored")) {
                    registerFix(builder, RemoveStrictKeywordQuickFix(SmartPointerManager.createPointer(cause)))
                }
                for (quickFix in error.quickFixes) {
                    val sourceNode = quickFix.replacement
                    if (sourceNode == null) {
                        val target = getTargetPsiElement(quickFix, cause)
                        when (val parent = target?.ancestor<ArendExpr>()?.topmostEquivalentSourceNode?.parent) {
                            is ArendArgument -> registerFix(builder, RemoveArgumentQuickFix(quickFix.message, SmartPointerManager.createPointer(parent)))
                            is ArendTupleExpr -> registerFix(builder, RemoveTupleExprQuickFix(quickFix.message, SmartPointerManager.createPointer(parent), true))
                        }
                    }
                }
            }
        }
        addHighlightInfo(builder)
    }

    override fun report(error: GeneralError) {
        errorList.add(error)
    }

    fun reportAll(errors: List<GeneralError>) {
        errorList.addAll(errors)
    }

    companion object {
        private fun getTargetPsiElement(quickFix: ErrorQuickFix, cause: PsiElement): PsiElement? =
            when (val target = quickFix.target) {
                null -> cause
                is ConcreteSourceNode -> target.data as? PsiElement
                else -> target as? PsiElement
            }

        fun levelToSeverity(level: GeneralError.Level): HighlightSeverity =
            when (level) {
                GeneralError.Level.ERROR -> HighlightSeverity.ERROR
                GeneralError.Level.WARNING -> HighlightSeverity.WARNING
                GeneralError.Level.WARNING_UNUSED -> HighlightSeverity.WEAK_WARNING
                GeneralError.Level.GOAL -> HighlightSeverity.WARNING
                GeneralError.Level.INFO -> HighlightSeverity.INFORMATION
            }

        fun levelToHighlightInfoType(level: GeneralError.Level): HighlightInfoType =
            when (level) {
                GeneralError.Level.ERROR -> HighlightInfoType.ERROR
                GeneralError.Level.WARNING -> HighlightInfoType.WARNING
                GeneralError.Level.WARNING_UNUSED -> HighlightInfoType.UNUSED_SYMBOL
                GeneralError.Level.GOAL -> HighlightInfoType.WARNING
                GeneralError.Level.INFO -> HighlightInfoType.INFORMATION
            }

        fun getCauseElement(data: Any?): PsiElement? {
            val cause = data?.let { (it as? DataContainer)?.data ?: it }
            return ((cause as? SmartPsiElementPointer<*>)?.let { runReadAction { it.element } } ?: cause) as? PsiElement
        }

        private fun getImprovedErrorElement(error: GeneralError?, element: PsiElement): PsiElement? {
            val result = when (error) {
                is NotInScopeError -> (element as? ArendStatCmd)?.longName
                is ParsingError -> when (error.kind) {
                    MISPLACED_USE -> (element as? ArendDefFunction)?.functionKw?.firstRelevantChild
                    MISPLACED_COERCE, COERCE_WITHOUT_PARAMETERS -> (element as? ArendDefFunction)?.functionKw?.firstRelevantChild?.findNextSibling()
                    CLASSIFYING_FIELD_IN_RECORD -> when (element) {
                        is ArendFieldDefIdentifier -> element.parent?.let { (it as? ArendFieldTele)?.classifyingKw ?: it }
                        is ArendDefClass -> element.noClassifyingKw ?: element.fieldTeleList.firstNotNullOfOrNull { it.classifyingKw }
                        else -> element
                    }
                    INVALID_PRIORITY -> (element as? ReferableBase<*>)?.prec?.number
                    MISPLACED_IMPORT -> (element as? ArendStatCmd)?.importKw
                    else -> null
                }
                is CertainTypecheckingError -> when (error.kind) {
                    CertainTypecheckingError.Kind.LEVEL_IGNORED -> element.ancestor<ArendReturnExpr>()?.levelKw
                    TRUNCATED_WITHOUT_UNIVERSE -> (element as? ArendDefData)?.truncatedKw
                    CASE_RESULT_TYPE -> (element as? ArendCaseExpr)?.caseKw
                    COULD_BE_LEMMA, AXIOM_WITH_BODY -> (element as? ArendDefFunction)?.functionKw
                    else -> null
                }
                is LevelMismatchError -> when (element) {
                    is ArendDefFunction -> element.functionKw.firstChild
                    is ArendClassField -> (element.parent as? ArendClassStat)?.propertyKw
                    is ArendTypeTele -> element.propertyKw
                    is ArendNameTele -> element.propertyKw
                    else -> null
                }
                is ExpectedConstructorError -> (element as? ArendPattern)?.firstChild
                is ImplicitLambdaError -> error.parameter?.underlyingReferable as? PsiElement
                is LambdaInferenceError -> error.parameter?.underlyingReferable as? PsiElement
                else -> null
            }

            return result ?: when (element) {
                is PsiLocatedReferable -> element.nameIdentifier
                is CoClauseBase -> element.longName
                else -> null
            }
        }

        fun getImprovedCause(error: GeneralError) = getCauseElement(error.cause)?.let {
            getImprovedErrorElement(error, it) ?: it
        }

        fun getImprovedTextRange(error: GeneralError) = getCauseElement(error.cause)?.let { getImprovedTextRange(error, it) }

        fun getImprovedTextRange(error: GeneralError?, element: PsiElement): TextRange {
            val improvedElement = getImprovedErrorElement(error, element) ?: element

            ((improvedElement as? ArendDefIdentifier)?.parent as? ArendDefinition<*>)?.let {
                return TextRange(it.textRange.startOffset, improvedElement.textRange.endOffset)
            }

            (((improvedElement as? ArendRefIdentifier)?.parent as? ArendLongName)?.parent as? ArendCoClause)?.let {
                return TextRange(it.textRange.startOffset, improvedElement.textRange.endOffset)
            }

            ((improvedElement as? ArendLongName)?.parent as? CoClauseBase)?.let { coClause ->
                val endElement = coClause.expr?.let { if (isEmptyGoal(it)) it else null } ?: coClause.fatArrow
                ?: coClause.lbrace ?: improvedElement
                return TextRange(coClause.textRange.startOffset, endElement.textRange.endOffset)
            }

            (improvedElement as? ArendGoal)?.let {
                if (it.expr != null) {
                    return TextRange(it.textRange.startOffset, improvedElement.textRange.endOffset)
                }
            }

            if ((error as? CertainTypecheckingError)?.kind == BODY_IGNORED) {
                (improvedElement as? ArendExpr ?: improvedElement.parent as? ArendExpr)?.let { expr ->
                    (expr.topmostEquivalentSourceNode.parentSourceNode as? ArendClause)?.let { clause ->
                        return TextRange((clause.fatArrow ?: expr).textRange.startOffset, expr.textRange.endOffset)
                    }
                }
            }

            if (improvedElement is ArendClause) {
                val prev = improvedElement.extendLeft.prevSibling
                val startElement = if (prev is LeafPsiElement && prev.elementType == PIPE) prev else improvedElement
                val endOffset =
                        if (error is ConditionsError) (improvedElement.patterns.lastOrNull() as? PsiElement
                                ?: improvedElement as PsiElement).textRange.endOffset
                        else improvedElement.textRange.endOffset
                return TextRange(startElement.textRange.startOffset, endOffset)
            }

            if ((error as? CertainTypecheckingError)?.kind == TOO_MANY_PATTERNS && improvedElement is ArendPattern) {
                var endElement: ArendPattern = improvedElement
                while (true) {
                    var next = endElement.extendRight.nextSibling
                    if (next is LeafPsiElement && next.elementType == COMMA) {
                        next = next.extendRight.nextSibling
                    }
                    if (next is ArendPattern) {
                        endElement = next
                    } else {
                        break
                    }
                }
                return TextRange(improvedElement.textRange.startOffset, endElement.textRange.endOffset)
            }

            if ((error is GoalError || error == null) && isIncomplete(improvedElement)) {
                if (improvedElement !is LeafPsiElement) {
                    val offset = improvedElement.textRange.endOffset
                    return TextRange(offset, offset + if (improvedElement.nextElement == null) 0 else 1)
                }

                var next = improvedElement.nextSibling
                if (next is PsiWhiteSpace) {
                    val text = next.text
                    val first = text.indexOf('\n')
                    if (first == -1) {
                        next = next.nextSibling
                    } else {
                        val second = text.indexOf('\n', first + 1)
                        val offset = next.textRange.startOffset
                        return if (second == -1) TextRange(offset + first + 1, offset + first + 2)
                        else TextRange(offset + second, offset + second + 1)
                    }
                }
                if (next != null) {
                    val offset = next.textRange.startOffset
                    return TextRange(offset, offset + 1)
                }
            }

            if (improvedElement is LeafPsiElement && STATEMENT_WT_KWS_TOKENS.contains(improvedElement.elementType)) {
                val stat = improvedElement.ancestor<ArendDefinition<*>>()?.parent
                if (stat != null) {
                    return TextRange(stat.startOffset, improvedElement.endOffset)
                }
            }

            return improvedElement.textRange
        }

        fun getImprovedTextOffset(error: GeneralError?, element: PsiElement) =
                getImprovedTextRange(error, element).startOffset

        fun isIncomplete(element: PsiElement) =
                element is ArendLetExpr && element.expr == null ||
                element is ArendLamExpr && element.body == null ||
                element is LeafPsiElement && element.elementType.let { it == COMMA || it == LBRACE }

        private val GOAL_IN_COPATTERN_PREFIX: Array<Class<out PsiElement>> =
                arrayOf(ArendLiteral::class.java, ArendAtom::class.java, ArendAtomFieldsAcc::class.java, ArendArgumentAppExpr::class.java, ArendNewExpr::class.java)
        private val GOAL_IN_COPATTERN = StandardPatterns.or(withAncestors(*(GOAL_IN_COPATTERN_PREFIX + arrayOf(ArendLocalCoClause::class.java))),
                withAncestors(*(GOAL_IN_COPATTERN_PREFIX + arrayOf(ArendCoClause::class.java))))

        fun isEmptyGoal(element: PsiElement): Boolean {
            val goal: ArendGoal? = element.descendantOfType()
            return goal != null && GOAL_IN_COPATTERN.accepts(goal)
        }
    }
}
