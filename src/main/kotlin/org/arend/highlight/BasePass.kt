package org.arend.highlight

import com.google.common.html.HtmlEscapers
import com.intellij.codeInsight.daemon.impl.*
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.Annotation
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.arend.annotation.ArendImportHintAction
import org.arend.codeInsight.completion.withAncestors
import org.arend.error.Error
import org.arend.error.GeneralError
import org.arend.error.doc.DocFactory.vHang
import org.arend.error.doc.DocStringBuilder
import org.arend.naming.error.NamingError
import org.arend.naming.error.NamingError.Kind.*
import org.arend.naming.error.NotInScopeError
import org.arend.naming.reference.DataContainer
import org.arend.psi.*
import org.arend.psi.ext.*
import org.arend.psi.ext.impl.ReferableAdapter
import org.arend.quickfix.*
import org.arend.quickfix.AbstractEWCCAnnotator.Companion.makeFieldList
import org.arend.term.abs.IncompleteExpressionError
import org.arend.term.prettyprint.PrettyPrinterConfig
import org.arend.typechecking.error.LocalErrorReporter
import org.arend.typechecking.error.ProxyError
import org.arend.typechecking.error.local.*
import org.arend.typechecking.error.local.TypecheckingError.Kind.*

abstract class BasePass(protected val file: ArendFile, editor: Editor, name: String, private val textRange: TextRange, highlightInfoProcessor: HighlightInfoProcessor)
    : ProgressableTextEditorHighlightingPass(file.project, editor.document, name, file, editor, textRange, false, highlightInfoProcessor), LocalErrorReporter {

    protected val holder = AnnotationHolderImpl(AnnotationSession(file))

    override fun getDocument(): Document = super.getDocument()!!

    override fun applyInformationWithProgress() {
        val highlights = holder.map { HighlightInfo.fromAnnotation(it) }
        ApplicationManager.getApplication().invokeLater({
            if (isValid) {
                UpdateHighlightersUtil.setHighlightersToEditor(myProject, document, textRange.startOffset, textRange.endOffset, highlights, colorsScheme, id)
            }
        }, ModalityState.stateForComponent(editor.component))
    }

    private fun createAnnotation(error: Error, range: TextRange): Annotation {
        val ppConfig = PrettyPrinterConfig.DEFAULT
        return holder.createAnnotation(levelToSeverity(error.level), range, error.shortMessage, HtmlEscapers.htmlEscaper().escape(DocStringBuilder.build(vHang(error.getShortHeaderDoc(ppConfig), error.getBodyDoc(ppConfig)))).replace("\n", "<br>"))
    }

    fun report(error: Error, cause: ArendCompositeElement) {
        val localError = error.localError
        if (localError is IncompleteExpressionError || file != cause.containingFile) {
            return
        }

        if (localError is NotInScopeError) {
            val ref = when (cause) {
                is ArendReferenceElement -> cause
                is ArendLongName -> cause.refIdentifierList.getOrNull(localError.index)
                else -> null
            }
            when (val resolved = ref?.reference?.resolve()) {
                is PsiDirectory -> holder.createErrorAnnotation(ref, "Unexpected reference to a directory")
                is PsiFile -> holder.createErrorAnnotation(ref, "Unexpected reference to a file")
                else -> {
                    val annotation = createAnnotation(error, (ref ?: cause).textRange)
                    if (resolved == null) {
                        annotation.highlightType = ProblemHighlightType.ERROR
                        if (ref != null && localError.index == 0) {
                            val fix = ArendImportHintAction(ref)
                            if (fix.isAvailable(myProject, null, file)) {
                                annotation.registerFix(fix)
                            }
                        }
                    }
                }
            }
        } else {
            val annotation = createAnnotation(error, getImprovedTextRange(error, cause))
            when (localError) {
                is FieldsImplementationError ->
                    if (localError.alreadyImplemented) {
                        (localError.cause.data as? ArendCoClause)?.let {
                            annotation.registerFix(RemoveCoClauseQuickFix(it))
                        }
                    } else {
                        val element = getCauseElement(localError.cause.data)
                        AbstractEWCCAnnotator.makeAnnotator(element)?.let {
                            annotation.registerFix(ImplementFieldsQuickFix(it, makeFieldList(localError.fields, localError.classReferable)))
                        }
                        if (element is ArendNewExprImplMixin) {
                            element.putUserData(CoClausesKey, null)
                        }
                    }
                is DesugaringError -> if (localError.kind == DesugaringError.Kind.REDUNDANT_COCLAUSE) {
                    annotation.highlightType = ProblemHighlightType.LIKE_UNUSED_SYMBOL
                    (getCauseElement(localError.cause.data) as? ArendCoClause)?.let {
                        annotation.registerFix(RemoveCoClauseQuickFix(it))
                    }
                }
                is TypecheckingError -> {
                    if (localError.level == Error.Level.WEAK_WARNING) {
                        annotation.highlightType = ProblemHighlightType.LIKE_UNUSED_SYMBOL
                    }
                    when (localError.kind) {
                        TOO_MANY_PATTERNS -> (getCauseElement(localError.cause.data) as? ArendPatternImplMixin)?.let {
                            annotation.registerFix(RemovePatternsQuickFix(it))
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    fun report(error: Error) {
        if (error is IncompleteExpressionError) {
            return
        }

        val list = error.cause?.let { it as? Collection<*> ?: listOf(it) } ?: return
        for (cause in list) {
            val psi = getCauseElement(cause)
            if (psi != null && psi.isValid) {
                report(error, psi)
            }
        }
    }

    override fun report(error: LocalError) {
        report(error as Error)
    }

    override fun report(error: GeneralError) {
        report(error as Error)
    }

    companion object {
        fun levelToSeverity(level: Error.Level): HighlightSeverity =
            when (level) {
                Error.Level.ERROR -> HighlightSeverity.ERROR
                Error.Level.WARNING -> HighlightSeverity.WARNING
                Error.Level.WEAK_WARNING -> HighlightSeverity.WEAK_WARNING
                Error.Level.GOAL -> HighlightSeverity.WARNING
                Error.Level.INFO -> HighlightSeverity.INFORMATION
            }

        private fun getCauseElement(data: Any?): ArendCompositeElement? {
            val cause = data?.let { (it as? DataContainer)?.data ?: it }
            return ((cause as? SmartPsiElementPointer<*>)?.let { runReadAction { it.element } } ?: cause) as? ArendCompositeElement
        }

        private fun getImprovedErrorElement(error: Error?, element: ArendCompositeElement): PsiElement? {
            val result = when (error) {
                is NamingError -> when (error.kind) {
                    MISPLACED_USE -> (element as? ArendDefFunction)?.useKw
                    MISPLACED_COERCE, COERCE_WITHOUT_PARAMETERS -> (element as? ArendDefFunction)?.coerceKw
                    LEVEL_IN_FIELD -> element.ancestors.filterIsInstance<ArendReturnExpr>().firstOrNull()?.levelKw
                    CLASSIFYING_FIELD_IN_RECORD -> (element as? ArendFieldDefIdentifier)?.parent
                    INVALID_PRIORITY -> (element as? ReferableAdapter<*>)?.getPrec()?.number
                    null -> null
                }
                is TypecheckingError -> when (error.kind) {
                    LEVEL_IN_FUNCTION -> element.ancestors.filterIsInstance<ArendReturnExpr>().firstOrNull()?.levelKw
                    else -> null
                }
                is ProxyError -> return getImprovedErrorElement(error.localError, element)
                is ExpectedConstructor -> (element as? ArendPattern)?.firstChild
                else -> null
            }

            return result ?: when (element) {
                is PsiLocatedReferable -> element.defIdentifier
                is CoClauseBase -> element.longName
                else -> null
            }
        }

        fun getImprovedCause(error: Error) = getCauseElement(error.cause)?.let { getImprovedErrorElement(error, it) }

        fun getImprovedTextRange(error: Error?, element: ArendCompositeElement): TextRange {
            val improvedElement = getImprovedErrorElement(error, element) ?: element

            ((improvedElement as? ArendDefIdentifier)?.parent as? ArendDefinition)?.let {
                return TextRange(it.textRange.startOffset, improvedElement.textRange.endOffset)
            }

            ((improvedElement as? ArendLongName)?.parent as? CoClauseBase)?.let { coClause ->
                val endElement = coClause.expr?.let { if (isEmptyGoal(it)) it else null } ?: coClause.fatArrow ?: coClause.lbrace ?: improvedElement
                return TextRange(improvedElement.textRange.startOffset, endElement.textRange.endOffset)
            }

            if (improvedElement is ArendExpr) {
                (improvedElement.topmostEquivalentSourceNode.parentSourceNode as? ArendClause)?.let {
                    return TextRange((it.fatArrow ?: improvedElement).textRange.startOffset, improvedElement.textRange.endOffset)
                }
            }

            if (improvedElement is ArendClause) {
                val prev = improvedElement.extendLeft.prevSibling
                val startElement = if (prev is LeafPsiElement && prev.elementType == ArendElementTypes.PIPE) prev else improvedElement
                return TextRange(startElement.textRange.startOffset, improvedElement.textRange.endOffset)
            }

            if ((error as? TypecheckingError)?.kind == TOO_MANY_PATTERNS && improvedElement is ArendPatternImplMixin) {
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

            return improvedElement.textRange
        }

        private val GOAL_IN_COPATTERN = withAncestors(ArendLiteral::class.java, ArendAtom::class.java, ArendAtomFieldsAcc::class.java,
            ArendArgumentAppExpr::class.java, ArendNewExpr::class.java, ArendCoClause::class.java)

        fun isEmptyGoal(element: PsiElement): Boolean {
            val goal: ArendGoal? = element.childOfType()
            return goal != null && GOAL_IN_COPATTERN.accepts(goal)
        }
    }
}