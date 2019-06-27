package org.arend.highlight

import com.intellij.codeInsight.daemon.impl.*
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.Annotation
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runReadAction
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
import org.arend.error.GeneralError
import org.arend.error.doc.DocFactory.vHang
import org.arend.error.doc.DocStringBuilder
import org.arend.naming.error.NamingError
import org.arend.naming.error.NamingError.Kind.*
import org.arend.naming.error.NotInScopeError
import org.arend.naming.reference.DataContainer
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.impl.ArendGroup
import org.arend.psi.ext.impl.ReferableAdapter
import org.arend.term.concrete.Concrete
import org.arend.term.group.Group
import org.arend.term.prettyprint.PrettyPrinterConfig
import org.arend.typechecking.error.LocalErrorReporter
import org.arend.typechecking.error.ProxyError
import org.arend.typechecking.error.local.LocalError
import org.arend.typechecking.typecheckable.provider.ConcreteProvider

abstract class BasePass(protected val file: ArendFile, protected val group: ArendGroup, editor: Editor, name: String, private val textRange: TextRange, highlightInfoProcessor: HighlightInfoProcessor)
    : ProgressableTextEditorHighlightingPass(file.project, editor.document, name, file, editor, textRange, false, highlightInfoProcessor) {

    protected val holder = AnnotationHolderImpl(AnnotationSession(file))
    protected val errorReporter = object : LocalErrorReporter {
        override fun report(error: LocalError) {
            processError(error, holder)
        }

        override fun report(error: GeneralError) {
            processError(error, holder)
        }
    }

    override fun getDocument(): Document = super.getDocument()!!

    open fun visitDefinition(definition: Concrete.Definition, concreteProvider: ConcreteProvider, progress: ProgressIndicator) {}

    private fun visitGroup(group: ArendGroup, concreteProvider: ConcreteProvider, progress: ProgressIndicator) {
        (group.referable as? ArendDefinition)?.let {
            (concreteProvider.getConcrete(it) as? Concrete.Definition)?.let { def ->
                visitDefinition(def, concreteProvider, progress)
                progress.checkCanceled()
            }
            advanceProgress(1)
        }
        for (subgroup in group.subgroups) {
            visitGroup(subgroup, concreteProvider, progress)
        }
        for (subgroup in group.dynamicSubgroups) {
            visitGroup(subgroup, concreteProvider, progress)
        }
    }

    open fun collectInfo(progress: ProgressIndicator) {
        visitGroup(group, myProject.getComponent(ArendHighlightingPassFactory::class.java).concreteProvider, progress)
    }

    override fun collectInformationWithProgress(progress: ProgressIndicator) {
        setProgressLimit(numberOfDefinitions(group).toLong() - 1)
        collectInfo(progress)
    }

    override fun applyInformationWithProgress() {
        val highlights = holder.map { HighlightInfo.fromAnnotation(it) }
        ApplicationManager.getApplication().invokeLater({
            if (isValid) {
                UpdateHighlightersUtil.setHighlightersToEditor(myProject, document, textRange.startOffset, textRange.endOffset, highlights, colorsScheme, id)
            }
        }, ModalityState.stateForComponent(editor.component))
    }

    companion object {
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

        private fun createAnnotation(error: Error, range: TextRange, holder: AnnotationHolder): Annotation {
            val ppConfig = PrettyPrinterConfig.DEFAULT
            return holder.createAnnotation(levelToSeverity(error.level), range, error.shortMessage, DocStringBuilder.build(vHang(error.getShortHeaderDoc(ppConfig), error.getBodyDoc(ppConfig))))
        }

        private fun processError(error: Error, holder: AnnotationHolder) {
            val psi = getCauseElement(error)
            if (psi != null && psi.isValid) {
                processError(error, psi, holder)
            }
        }

        fun processError(error: Error, cause: ArendCompositeElement, holder: AnnotationHolder) {
            val localError = error as? LocalError ?: (error as? ProxyError)?.localError
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
                        val annotation = createAnnotation(error, (ref ?: cause).textRange, holder)
                        if (resolved == null) {
                            annotation.highlightType = ProblemHighlightType.ERROR
                            if (ref != null && localError.index == 0) {
                                val fix = ArendImportHintAction(ref)
                                val file = cause.containingFile
                                if (fix.isAvailable(file.project, null, file)) {
                                    annotation.registerFix(fix)
                                }
                            }
                        }
                    }
                }
            } else {
                createAnnotation(error, (getImprovedErrorElement(localError, cause) ?: cause).textRange, holder)
            }
        }
    }
}