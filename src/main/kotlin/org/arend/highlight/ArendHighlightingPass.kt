package org.arend.highlight

import com.intellij.codeInsight.daemon.impl.*
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.Annotation
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.annotation.ArendImportHintAction
import org.arend.error.Error
import org.arend.error.ErrorReporter
import org.arend.error.GeneralError
import org.arend.error.doc.DocFactory.vHang
import org.arend.error.doc.DocStringBuilder
import org.arend.naming.error.NamingError
import org.arend.naming.error.NamingError.Kind.*
import org.arend.naming.error.NotInScopeError
import org.arend.naming.reference.*
import org.arend.naming.resolving.ResolverListener
import org.arend.naming.resolving.visitor.DefinitionResolveNameVisitor
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.impl.ArendGroup
import org.arend.psi.ext.impl.ReferableAdapter
import org.arend.resolving.ArendResolveCache
import org.arend.resolving.PsiConcreteProvider
import org.arend.resolving.WrapperReferableConverter
import org.arend.term.concrete.Concrete
import org.arend.term.group.Group
import org.arend.term.prettyprint.PrettyPrinterConfig
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.error.ProxyError

class ArendHighlightingPass(private val file: ArendFile, private val group: ArendGroup, editor: Editor, private val textRange: TextRange, highlightInfoProcessor: HighlightInfoProcessor) : ProgressableTextEditorHighlightingPass(file.project, editor.document, "Arend resolver annotator", file, editor, textRange, false, highlightInfoProcessor) {
    private val holder = AnnotationHolderImpl(AnnotationSession(file))

    override fun getDocument(): Document = super.getDocument()!!

    override fun collectInformationWithProgress(progress: ProgressIndicator) {
        setProgressLimit(numberOfDefinitions(group).toLong() - 1)

        val project = file.project
        val errorReporter = ErrorReporter { error -> processError(error) }
        val concreteProvider = PsiConcreteProvider(project, WrapperReferableConverter, errorReporter, null, false)
        val resolverCache = ServiceManager.getService(project, ArendResolveCache::class.java)
        DefinitionResolveNameVisitor(concreteProvider, errorReporter, object : ResolverListener {
            private fun resolveReference(data: Any?, referent: Referable, originalRef: Referable?) {
                val reference = (data as? ArendLongName)?.refIdentifierList?.lastOrNull() ?: data as? ArendReferenceElement ?: return
                if (reference is ArendRefIdentifier && referent is GlobalReferable && referent.precedence.isInfix) {
                    holder.createInfoAnnotation(reference, null).textAttributes = ArendHighlightingColors.OPERATORS.textAttributesKey
                }
                if (data is ArendLongName) {
                    val nextToLast = reference.prevSibling
                    if (nextToLast != null) {
                        holder.createInfoAnnotation(TextRange(data.textRange.startOffset, nextToLast.textRange.endOffset), null).textAttributes = ArendHighlightingColors.LONG_NAME.textAttributesKey
                    }
                }
                if (referent != originalRef) {
                    resolverCache.resolveCached({ if (referent is ErrorReference) null else referent }, reference)
                }
            }

            override fun referenceResolved(argument: Concrete.Expression?, originalRef: Referable, refExpr: Concrete.ReferenceExpression) {
                resolveReference(refExpr.data, refExpr.referent, originalRef)

                var arg = argument
                while (arg is Concrete.AppExpression) {
                    for (arg1 in arg.arguments) {
                        (arg1.expression as? Concrete.ReferenceExpression)?.let {
                            resolveReference(it.data, it.referent, null)
                        }
                    }
                    arg = arg.function
                }
                (arg as? Concrete.ReferenceExpression)?.let {
                    resolveReference(it.data, it.referent, null)
                }
            }

            override fun patternResolved(originalRef: Referable, pattern: Concrete.ConstructorPattern) {
                resolveReference(pattern.data, pattern.constructor, originalRef)
            }

            override fun definitionResolved(definition: Concrete.Definition) {
                progress.checkCanceled()
                (definition.data.data as? PsiLocatedReferable)?.defIdentifier?.let {
                    holder.createInfoAnnotation(it, null).textAttributes = ArendHighlightingColors.DECLARATION.textAttributesKey
                }
                advanceProgress(1)
            }
        }).resolveGroup(group, null, group.scope)

        for (error in TypeCheckingService.getInstance(file.project).getErrors(file)) {
            progress.checkCanceled()
            processError(error)
        }
    }

    private fun numberOfDefinitions(group: Group): Int {
        var res = if (group.referable is ArendDefinition) 1 else 0
        for (subgroup in group.subgroups) {
            res += numberOfDefinitions(subgroup)
        }
        for (subgroup in group.dynamicSubgroups) {
            res += numberOfDefinitions(subgroup)
        }
        return res
    }

    override fun applyInformationWithProgress() {
        val highlights = holder.map { HighlightInfo.fromAnnotation(it) }
        ApplicationManager.getApplication().invokeLater({
            if (isValid) {
                UpdateHighlightersUtil.setHighlightersToEditor(file.project, document, textRange.startOffset, textRange.endOffset, highlights, colorsScheme, id)
            }
        }, ModalityState.stateForComponent(editor.component))
    }

    companion object {
        fun levelToSeverity(level: Error.Level): HighlightSeverity =
            when (level) {
                Error.Level.ERROR -> HighlightSeverity.ERROR
                Error.Level.WARNING -> HighlightSeverity.WARNING
                Error.Level.GOAL -> HighlightSeverity.WARNING
                Error.Level.INFO -> HighlightSeverity.INFORMATION
            }

        private fun getImprovedErrorElement(error: Error?, element: ArendCompositeElement): PsiElement? = when (error) {
            is NamingError -> when (error.kind) {
                USE_IN_CLASS -> (element as? ArendDefFunction)?.useKw
                LEVEL_IN_FIELD -> element.ancestorsUntilFile.filterIsInstance<ArendReturnExpr>().firstOrNull()?.levelKw
                CLASSIFYING_FIELD_IN_RECORD -> (element as? ArendFieldDefIdentifier)?.parent
                INVALID_PRIORITY -> (element as? ReferableAdapter<*>)?.getPrec()?.number
                null -> null
            }
            is ProxyError -> getImprovedErrorElement(error.localError, element)
            else -> null
        }

        private fun getCauseElement(error: Error): ArendCompositeElement? {
            val cause = error.cause?.let { (it as? DataContainer)?.data ?: it }
            return ((cause as? SmartPsiElementPointer<*>)?.let { runReadAction { it.element } } ?: cause) as? ArendCompositeElement
        }

        fun getImprovedCause(error: Error) = getCauseElement(error)?.let { getImprovedErrorElement(error, it) }
    }

    private fun createAnnotation(error: GeneralError, range: TextRange): Annotation {
        val ppConfig = PrettyPrinterConfig.DEFAULT
        return holder.createAnnotation(levelToSeverity(error.level), range, error.shortMessage, DocStringBuilder.build(vHang(error.getShortHeaderDoc(ppConfig), error.getBodyDoc(ppConfig))))
    }

    private fun processError(error: GeneralError) {
        val psi = getCauseElement(error)
        if (psi != null && psi.isValid) {
            val localError = (error as? ProxyError)?.localError
            if (localError is NotInScopeError) {
                val ref = when (psi) {
                    is ArendReferenceElement -> psi
                    is ArendLongName -> psi.refIdentifierList.getOrNull(localError.index)
                    else -> null
                }
                when (val resolved = ref?.reference?.resolve()) {
                    is PsiDirectory -> holder.createErrorAnnotation(ref, "Unexpected reference to a directory")
                    is PsiFile -> holder.createErrorAnnotation(ref, "Unexpected reference to a file")
                    else -> {
                        val annotation = createAnnotation(error, (ref ?: psi).textRange)
                        if (resolved == null) {
                            annotation.highlightType = ProblemHighlightType.ERROR
                            if (ref != null && localError.index == 0) {
                                val fix = ArendImportHintAction(ref)
                                val file = psi.containingFile
                                if (fix.isAvailable(file.project, null, file)) {
                                    annotation.registerFix(fix)
                                }
                            }
                        }
                    }
                }
            } else {
                createAnnotation(error, (getImprovedErrorElement(localError, psi) ?: psi).textRange)
            }
        }
    }
}