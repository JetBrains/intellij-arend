package org.arend.highlight

import com.intellij.codeInsight.daemon.impl.*
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.QuickFix
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.xml.util.XmlStringUtil
import org.arend.codeInsight.completion.withAncestors
import org.arend.core.context.param.DependentLink
import org.arend.core.expr.ReferenceExpression
import org.arend.error.ParsingError
import org.arend.error.ParsingError.Kind.*
import org.arend.ext.error.*
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
import org.arend.psi.ext.*
import org.arend.psi.ext.impl.ReferableAdapter
import org.arend.quickfix.*
import org.arend.quickfix.implementCoClause.CoClausesKey
import org.arend.quickfix.implementCoClause.ImplementFieldsQuickFix
import org.arend.quickfix.implementCoClause.makeFieldList
import org.arend.quickfix.instance.AddInstanceArgumentQuickFix
import org.arend.quickfix.instance.InstanceInferenceQuickFix
import org.arend.quickfix.instance.ReplaceWithLocalInstanceQuickFix
import org.arend.quickfix.referenceResolve.ArendImportHintAction
import org.arend.quickfix.removers.*
import org.arend.refactoring.replaceExprSmart
import org.arend.resolving.DataLocatedReferable
import org.arend.term.abs.IncompleteExpressionError
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.PrettyPrinterConfigWithRenamer
import org.arend.typechecking.error.ArendError
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.error.local.*
import org.arend.typechecking.error.local.CertainTypecheckingError.Kind.*
import org.arend.typechecking.error.local.inference.InstanceInferenceError
import org.arend.util.ArendBundle
import java.util.*

abstract class BasePass(protected val file: ArendFile, editor: Editor, name: String, protected val textRange: TextRange, highlightInfoProcessor: HighlightInfoProcessor)
    : ProgressableTextEditorHighlightingPass(file.project, editor.document, name, file, editor, textRange, false, highlightInfoProcessor), ErrorReporter {

    private val highlights = ArrayList<HighlightInfo>()
    private val errorList = ArrayList<GeneralError>()

    override fun applyInformationWithProgress() {
        val errorService = myProject.service<ErrorService>()

        for (error in errorList) {
            val list = error.cause?.let { it as? Collection<*> ?: listOf(it) } ?: return
            for (cause in list) {
                val psi = getCauseElement(cause)
                if (psi != null && psi.isValid) {
                    reportToEditor(error, psi)
                    errorService.report(ArendError(error, runReadAction { SmartPointerManager.createPointer(psi) }))
                }
            }
        }

        ApplicationManager.getApplication().invokeLater({
            if (isValid) {
                UpdateHighlightersUtil.setHighlightersToEditor(myProject, document, textRange.startOffset, textRange.endOffset, highlights, colorsScheme, id)
            }
        }, ModalityState.stateForComponent(editor.component))
    }

    protected fun addHighlightInfo(builder: HighlightInfo.Builder): HighlightInfo? {
        val info = builder.create()
        if (info != null) {
            highlights.add(info)
        }
        return info
    }

    protected fun addHighlightInfo(range: TextRange, colors: ArendHighlightingColors) {
        addHighlightInfo(HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION).range(range).textAttributes(colors.textAttributesKey))
    }

    private fun createHighlightInfoBuilder(error: GeneralError, range: TextRange, type: HighlightInfoType? = null): HighlightInfo.Builder {
        val ppConfig = PrettyPrinterConfigWithRenamer(EmptyScope.INSTANCE)
        ppConfig.expressionFlags = EnumSet.of(PrettyPrinterFlag.SHOW_LOCAL_FIELD_INSTANCE)
        return HighlightInfo.newHighlightInfo(type ?: levelToHighlightInfoType(error.level))
            .range(range)
            .severity(levelToSeverity(error.level))
            .description(error.shortMessage)
            .escapedToolTip(XmlStringUtil.escapeString(DocStringBuilder.build(vHang(error.getShortHeaderDoc(ppConfig), error.getBodyDoc(ppConfig)))).replace("\n", "<br>").replace(" ", "&nbsp;"))
    }

    private fun registerFix(info: HighlightInfo, fix: IntentionAction) {
        info.registerFix(fix, null, fix.text, null, null)
    }

    fun reportToEditor(error: GeneralError, cause: PsiElement) {
        if (error is IncompleteExpressionError || file != cause.containingFile) {
            return
        }

        if (error is NotInScopeError) {
            val ref: ArendReferenceElement? = when (cause) {
                is ArendIPNameImplMixin -> cause.parentLongName?.refIdentifierList?.getOrNull(error.index) ?: cause
                is ArendLongName -> cause.refIdentifierList.getOrNull(error.index)
                is ArendReferenceElement -> cause
                else -> null
            }
            when (val resolved = ref?.reference?.resolve()) {
                is PsiDirectory -> addHighlightInfo(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(ref).descriptionAndTooltip("Unexpected reference to a directory"))
                is PsiFile -> addHighlightInfo(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(ref).descriptionAndTooltip("Unexpected reference to a file"))
                else -> {
                    val info = addHighlightInfo(createHighlightInfoBuilder(error, ref?.textRange ?: getImprovedTextRange(error, cause), if (resolved == null) HighlightInfoType.WRONG_REF else null))
                    if (resolved == null && ref != null && error.index == 0 && info != null) {
                        val fix = ArendImportHintAction(ref)
                        if (fix.isAvailable(myProject, null, file)) {
                            registerFix(info, fix)
                        }
                    }
                }
            }
        } else {
            val textRange = getImprovedTextRange(error, cause)
            val builder = createHighlightInfoBuilder(error, textRange)
            if (textRange.endOffset == textRange.startOffset + 1 && document.charsSequence[textRange.startOffset] == '\n') {
                builder.endOfLine()
            }

            if (error is GoalError) {
                val incomplete = isIncomplete(cause)
                if (incomplete && cause !is LeafPsiElement) {
                    val next = cause.nextElement
                    if (next == null || next is PsiWhiteSpace && next.text.firstOrNull().let { it == '\n' || it == '\r' }) {
                        builder.endOfLine()
                    }
                }
                val info = addHighlightInfo(builder) ?: return
                val coClauseBase = cause.ancestor<CoClauseBase>()
                val coClauseBaseFixData = coClauseBase?.getUserData(CoClausesKey)
                if (coClauseBaseFixData != null) registerFix(info, object : ImplementFieldsQuickFix(SmartPointerManager.createPointer(coClauseBase), true, coClauseBaseFixData) {
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
                        val action: (Document, Concrete.Expression, String) -> Unit = { doc, concrete, text ->
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
                                doc.insertString(offset, "$prefix$text")

                                if (reformat) {
                                    val file = cause.containingFile
                                    CodeStyleManager.getInstance(file.project).reformatText(file, offset, offset + prefix.length + text.length)
                                }
                            } else replaceExprSmart(doc, expr, null, expr.textRange, null, concrete, text)
                        }
                        registerFix(info, GoalSolverFillingQuickFix(expr, error, action))
                        for (solver in error.goalSolver.additionalSolvers) {
                            registerFix(info, InteractiveGoalSolverQuickFix(expr, error, solver, action))
                        }
                    }
                    cause is ArendGoal && cause.expr != null -> registerFix(info, GoalFillingQuickFix(cause))
                }
                return
            }

            val info = addHighlightInfo(builder) ?: return
            when (error) {
                is ParsingError -> when (error.kind) {
                    MISPLACED_IMPORT -> {
                        val errorCause = error.cause
                        if (errorCause is ArendStatCmd && errorCause.isValid) {
                            registerFix(info, MisplacedImportQuickFix(SmartPointerManager.createPointer(errorCause)))
                        }
                    }
                    else -> {
                    }
                }

                is DuplicateOpenedNameError -> {
                    val errorCause = error.cause
                    if (errorCause is PsiElement && errorCause.isValid) {
                        registerFix(info, RenameDuplicateNameQuickFix(SmartPointerManager.createPointer(errorCause), error.referable))
                        registerFix(info, HideImportQuickFix(SmartPointerManager.createPointer(errorCause), error.referable))
                    }
                }

                is ExistingOpenedNameError -> {
                    val errorCause = error.cause
                    if (errorCause is PsiElement && errorCause.isValid) {
                        registerFix(info, RenameDuplicateNameQuickFix(SmartPointerManager.createPointer(errorCause), null))
                        registerFix(info, HideImportQuickFix(SmartPointerManager.createPointer(errorCause), null))
                    }
                }

                is FieldsImplementationError ->
                    if (error.alreadyImplemented) {
                        val errorCause = error.cause.data
                        if (errorCause is ArendCoClause && errorCause.isValid) {
                            registerFix(info, RemoveCoClauseQuickFix(SmartPointerManager.createPointer(errorCause)))
                        }
                    } else {
                        val ref = error.classRef
                        val classRef = if (ref is Referable) ref.underlyingReferable else ref
                        if (classRef is ClassReferable) {
                            registerFix(info, ImplementFieldsQuickFix(SmartPointerManager.createPointer(cause), false, makeFieldList(error.fields, classRef)))
                        }
                        if (cause is ArendNewExprImplMixin) {
                            cause.putUserData(CoClausesKey, null)
                        }
                    }

                is MissingClausesError -> if (cause is ArendCompositeElement)
                    registerFix(info, ImplementMissingClausesQuickFix(error, SmartPointerManager.createPointer(cause)))

                is ExpectedConstructorError -> if (cause is ArendCompositeElement)
                    registerFix(info, ExpectedConstructorQuickFix(error, SmartPointerManager.createPointer(cause)))

                is ImpossibleEliminationError -> if (cause is ArendCompositeElement)
                    registerFix(info, ImpossibleEliminationQuickFix(error, SmartPointerManager.createPointer(cause)))

                is DataTypeNotEmptyError -> if (cause is ArendCompositeElement)
                    registerFix(info, ReplaceAbsurdPatternQuickFix(error.constructors, SmartPointerManager.createPointer(cause)))

                is CertainTypecheckingError -> when (error.kind) {
                    TOO_MANY_PATTERNS, EXPECTED_EXPLICIT_PATTERN, IMPLICIT_PATTERN -> if (cause is ArendPatternImplMixin) {
                        val single = error.kind == EXPECTED_EXPLICIT_PATTERN
                        if (error.kind != TOO_MANY_PATTERNS) {
                            cause.atomPattern?.let {
                                if (it.isValid) {
                                    registerFix(info, MakePatternExplicitQuickFix(SmartPointerManager.createPointer(it), single))
                                }
                            }
                        }

                        if (!single || cause.nextSibling.findNextSibling { it is ArendPatternImplMixin } != null) {
                            registerFix(info, RemovePatternsQuickFix(SmartPointerManager.createPointer(cause), single))
                        }
                    }
                    AS_PATTERN_IGNORED -> if (cause is ArendAsPattern) registerFix(info, RemoveAsPatternQuickFix(SmartPointerManager.createPointer(cause)))
                    BODY_IGNORED ->
                        cause.ancestor<ArendClause>()?.let {
                            if (it.isValid) {
                                registerFix(info, RemovePatternRightHandSideQuickFix(SmartPointerManager.createPointer(it)))
                            }
                        }
                    PATTERN_IGNORED -> if (cause is ArendPatternImplMixin) registerFix(info, ReplaceWithWildcardPatternQuickFix(SmartPointerManager.createPointer(cause)))
                    else -> {}
                }

                is RedundantClauseError -> if (cause is ArendClause) registerFix(info, RemoveClauseQuickFix(SmartPointerManager.createPointer(cause)))

                is RedundantCoclauseError -> if (cause is ArendLocalCoClause) registerFix(info, RemoveCoClauseQuickFix(SmartPointerManager.createPointer(cause)))

                is InstanceInferenceError -> if (cause is ArendLongName) {
                    val classifyingExpression = error.classifyingExpression
                    val isLocal = (classifyingExpression is ReferenceExpression) && DependentLink.Helper.toList(((error as LocalError).definition as DataLocatedReferable).typechecked.parameters).contains(classifyingExpression.binding)
                    if (isLocal) registerFix(info, ReplaceWithLocalInstanceQuickFix(error, SmartPointerManager.createPointer(cause))) else registerFix(info, InstanceInferenceQuickFix(error, SmartPointerManager.createPointer(cause)))
                    registerFix(info, AddInstanceArgumentQuickFix(error, SmartPointerManager.createPointer(cause)))
                }

                is IgnoredArgumentError -> {
                    when (val parent = cause.ancestor<ArendExpr>()?.topmostEquivalentSourceNode?.parent) {
                        is ArendArgument -> registerFix(info, RemoveArgumentQuickFix(SmartPointerManager.createPointer(parent)))
                        is ArendTupleExpr -> registerFix(info, RemoveTupleExprQuickFix(SmartPointerManager.createPointer(parent), true))
                    }
                }
            }
        }
    }

    override fun report(error: GeneralError) {
        errorList.add(error)
    }

    companion object {
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
                    MISPLACED_USE -> (element as? ArendDefFunction)?.functionKw?.useKw
                    MISPLACED_COERCE, COERCE_WITHOUT_PARAMETERS -> (element as? ArendDefFunction)?.functionKw?.coerceKw
                    ParsingError.Kind.LEVEL_IGNORED -> element.ancestor<ArendReturnExpr>()?.levelKw
                    CLASSIFYING_FIELD_IN_RECORD -> when (element) {
                        is ArendFieldDefIdentifier -> element.parent?.let { (it as? ArendFieldTele)?.classifyingKw ?: it }
                        is ArendDefClass -> element.noClassifyingKw ?: element.fieldTeleList.mapNotNull { it.classifyingKw }.firstOrNull()
                        else -> element
                    }
                    INVALID_PRIORITY -> (element as? ReferableAdapter<*>)?.getPrec()?.number
                    MISPLACED_IMPORT -> (element as? ArendStatCmd)?.importKw
                    else -> null
                }
                is CertainTypecheckingError -> when (error.kind) {
                    CertainTypecheckingError.Kind.LEVEL_IGNORED -> element.ancestor<ArendReturnExpr>()?.levelKw
                    TRUNCATED_WITHOUT_UNIVERSE -> (element as? ArendDefData)?.truncatedKw
                    CASE_RESULT_TYPE -> (element as? ArendCaseExpr)?.caseOpt
                    else -> null
                }
                is LevelMismatchError -> if (error.isLemma) {
                    if (element is ArendDefFunction) element.functionKw.lemmaKw else element.ancestor<ArendReturnExpr>()?.levelKw
                } else {
                    ((element as? ArendClassField)?.parent as? ArendClassStat)?.propertyKw
                }
                is ExpectedConstructorError -> (element as? ArendPattern)?.firstChild
                is ImplicitLambdaError -> error.parameter?.underlyingReferable as? PsiElement
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

            ((improvedElement as? ArendDefIdentifier)?.parent as? ArendDefinition)?.let {
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
                    val range = it.textRange
                    return TextRange(range.startOffset, range.startOffset + 2)
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
                val startElement = if (prev is LeafPsiElement && prev.elementType == ArendElementTypes.PIPE) prev else improvedElement
                val endOffset =
                        if (error is ConditionsError) (improvedElement.patternList.lastOrNull()
                                ?: improvedElement as PsiElement).textRange.endOffset
                        else improvedElement.textRange.endOffset
                return TextRange(startElement.textRange.startOffset, endOffset)
            }

            if ((error as? CertainTypecheckingError)?.kind == TOO_MANY_PATTERNS && improvedElement is ArendPatternImplMixin) {
                var endElement: ArendPatternImplMixin = improvedElement
                while (true) {
                    var next = endElement.extendRight.nextSibling
                    if (next is LeafPsiElement && next.elementType == ArendElementTypes.COMMA) {
                        next = next.extendRight.nextSibling
                    }
                    if (next is ArendPatternImplMixin) {
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

            return improvedElement.textRange
        }

        fun getImprovedTextOffset(error: GeneralError?, element: PsiElement) =
                getImprovedTextRange(error, element).startOffset

        fun isIncomplete(element: PsiElement) =
                element is ArendLetExpr && element.expr == null ||
                element is ArendLamExpr && element.expr == null ||
                element is LeafPsiElement && element.elementType.let { it == ArendElementTypes.COMMA || it == ArendElementTypes.LBRACE }

        private val GOAL_IN_COPATTERN_PREFIX: Array<Class<out PsiElement>> =
                arrayOf(ArendLiteral::class.java, ArendAtom::class.java, ArendAtomFieldsAcc::class.java, ArendArgumentAppExpr::class.java, ArendNewExpr::class.java)
        private val GOAL_IN_COPATTERN = StandardPatterns.or(withAncestors(*(GOAL_IN_COPATTERN_PREFIX + arrayOf(ArendLocalCoClause::class.java))),
                withAncestors(*(GOAL_IN_COPATTERN_PREFIX + arrayOf(ArendCoClause::class.java))))

        fun isEmptyGoal(element: PsiElement): Boolean {
            val goal: ArendGoal? = element.childOfType()
            return goal != null && GOAL_IN_COPATTERN.accepts(goal)
        }
    }
}
