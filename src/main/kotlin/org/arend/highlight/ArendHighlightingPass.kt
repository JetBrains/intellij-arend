package org.arend.highlight

import com.intellij.codeInsight.daemon.impl.*
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.Annotation
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import org.arend.annotation.ArendImportHintAction
import org.arend.error.Error
import org.arend.error.GeneralError
import org.arend.error.ListErrorReporter
import org.arend.error.doc.DocFactory.vHang
import org.arend.error.doc.DocStringBuilder
import org.arend.naming.error.NotInScopeError
import org.arend.naming.reference.ErrorReference
import org.arend.naming.reference.Referable
import org.arend.naming.resolving.ResolverListener
import org.arend.naming.resolving.visitor.DefinitionResolveNameVisitor
import org.arend.psi.ArendFile
import org.arend.psi.ArendLongName
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.impl.ArendGroup
import org.arend.resolving.ArendResolveCache
import org.arend.resolving.PsiConcreteProvider
import org.arend.resolving.WrapperReferableConverter
import org.arend.term.concrete.Concrete
import org.arend.term.group.Group
import org.arend.term.prettyprint.PrettyPrinterConfig
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.error.ProxyError

class ArendHighlightingPass(private val file: ArendFile, private val group: ArendGroup, editor: Editor, textRange: TextRange, highlightInfoProcessor: HighlightInfoProcessor) : ProgressableTextEditorHighlightingPass(file.project, editor.document, "Arend resolver annotator", file, editor, textRange, false, highlightInfoProcessor) {
    private val errors = ArrayList<GeneralError>()

    override fun getDocument(): Document = super.getDocument()!!

    override fun collectInformationWithProgress(progress: ProgressIndicator) {
        setProgressLimit(numberOfDefinitions(group).toLong() - 1)

        val project = file.project
        val errorReporter = ListErrorReporter(errors)
        val concreteProvider = PsiConcreteProvider(project, WrapperReferableConverter, errorReporter, null, false)
        val resolverCache = ServiceManager.getService(project, ArendResolveCache::class.java)
        DefinitionResolveNameVisitor(concreteProvider, errorReporter, object : ResolverListener {
            private fun resolveReference(data: Any?, referent: Referable) {
                val reference = (data as? ArendLongName)?.refIdentifierList?.lastOrNull() ?: data as? ArendReferenceElement ?: return
                resolverCache.resolveCached({ if (referent is ErrorReference) null else referent }, reference)
            }

            override fun referenceResolved(argument: Concrete.Expression?, originalRef: Referable, refExpr: Concrete.ReferenceExpression) {
                resolveReference(refExpr.data, refExpr.referent)

                var arg = argument
                while (arg is Concrete.AppExpression) {
                    for (arg1 in arg.arguments) {
                        (arg1.expression as? Concrete.ReferenceExpression)?.let { resolveReference(it.data, it.referent) }
                    }
                    arg = arg.function
                }
                (arg as? Concrete.ReferenceExpression)?.let { resolveReference(it.data, it.referent) }
            }

            override fun patternResolved(originalRef: Referable, pattern: Concrete.ConstructorPattern) {
                resolveReference(pattern.data, pattern.constructor)
            }

            override fun definitionResolved(definition: Concrete.Definition?) {
                progress.checkCanceled()
                advanceProgress(1)
            }
        }).resolveGroup(group, null, group.scope)
    }

    private fun numberOfDefinitions(group: Group): Int {
        var res = 1
        for (subgroup in group.subgroups) {
            res += numberOfDefinitions(subgroup)
        }
        for (subgroup in group.dynamicSubgroups) {
            res += numberOfDefinitions(subgroup)
        }
        return res
    }

    override fun applyInformationWithProgress() {
        val holder = AnnotationHolderImpl(AnnotationSession(file))
        for (error in errors) {
            processError(error, holder)
        }
        for (error in TypeCheckingService.getInstance(file.project).getErrors(file)) {
            processError(error, holder)
        }

        val highlights = holder.map { HighlightInfo.fromAnnotation(it) }
        ApplicationManager.getApplication().invokeLater({
            if (isValid) {
                UpdateHighlightersUtil.setHighlightersToEditor(file.project, document, 0, document.textLength, highlights, colorsScheme, id)
            }
        }, ModalityState.stateForComponent(editor.component))
    }

    private fun levelToSeverity(level: Error.Level): HighlightSeverity =
        when (level) {
            Error.Level.ERROR -> HighlightSeverity.ERROR
            Error.Level.WARNING -> HighlightSeverity.WARNING
            Error.Level.GOAL -> HighlightSeverity.WARNING
            Error.Level.INFO -> HighlightSeverity.INFORMATION
        }

    private fun createAnnotation(error: GeneralError, range: TextRange, holder: AnnotationHolder): Annotation {
        val ppConfig = PrettyPrinterConfig.DEFAULT
        return holder.createAnnotation(levelToSeverity(error.level), range, error.shortMessage, DocStringBuilder.build(vHang(error.getShortHeaderDoc(ppConfig), error.getBodyDoc(ppConfig))))
    }

    private fun processError(error: GeneralError, holder: AnnotationHolder) {
        val psi = error.cause as? ArendCompositeElement
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
                        val annotation = createAnnotation(error, (ref ?: psi).textRange, holder)
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
                createAnnotation(error, psi.textRange, holder)
            }
        }
    }
}