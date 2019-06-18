package org.arend.annotation

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.arend.error.GeneralError
import org.arend.error.ListErrorReporter
import org.arend.error.doc.DocFactory.vHang
import org.arend.error.doc.DocStringBuilder
import org.arend.naming.reference.*
import org.arend.naming.reference.converter.ReferableConverter
import org.arend.naming.resolving.visitor.DefinitionResolveNameVisitor
import org.arend.psi.ArendFile
import org.arend.psi.ext.ArendCompositeElement
import org.arend.resolving.PsiConcreteProvider
import org.arend.resolving.TCReferableWrapper
import org.arend.term.prettyprint.PrettyPrinterConfig
import org.arend.typechecking.TypeCheckingService

class TypecheckerAnnotator : ExternalAnnotator<Collection<GeneralError>, Collection<GeneralError>>() {
    override fun collectInformation(file: PsiFile): Collection<GeneralError>? {
        if (file !is ArendFile) {
            return null
        }

        val errorReporter = ListErrorReporter()
        val referableConverter = object : ReferableConverter {
            override fun toDataReferable(referable: Referable?) = referable

            override fun toDataLocatedReferable(referable: LocatedReferable?) = TCReferableWrapper.wrap(referable)
        }
        val concreteProvider = PsiConcreteProvider(file.project, referableConverter, errorReporter, null)
        DefinitionResolveNameVisitor(concreteProvider, errorReporter).resolveGroup(file, null, file.scope)
        return errorReporter.errorList
    }

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean) = collectInformation(file)

    override fun doAnnotate(collectedInfo: Collection<GeneralError>?) = collectedInfo

    override fun apply(file: PsiFile, annotationResult: Collection<GeneralError>?, holder: AnnotationHolder) {
        if (file !is ArendFile) {
            return
        }

        if (annotationResult != null) {
            for (error in annotationResult) {
                processError(error, holder)
            }
        }
        for (error in TypeCheckingService.getInstance(file.project).getErrors(file)) {
            processError(error, holder)
        }
    }

    private fun processError(error: GeneralError, holder: AnnotationHolder) {
        val psi = error.cause as? ArendCompositeElement
        if (psi != null && psi.isValid) {
            val config = PrettyPrinterConfig.DEFAULT
            holder.createAnnotation(ArendHighlightingAnnotator.levelToSeverity(error.level), psi.textRange, error.shortMessage, DocStringBuilder.build(vHang(error.getShortHeaderDoc(config), error.getBodyDoc(config))))
        }
    }
}
