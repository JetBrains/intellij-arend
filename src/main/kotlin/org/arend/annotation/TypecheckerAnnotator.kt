package org.arend.annotation

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.arend.error.doc.DocFactory.vHang
import org.arend.error.doc.DocStringBuilder
import org.arend.psi.ArendFile
import org.arend.psi.ext.ArendCompositeElement
import org.arend.term.prettyprint.PrettyPrinterConfig
import org.arend.typechecking.TypeCheckingService

class TypecheckerAnnotator : ExternalAnnotator<InType, OutType>() {
    override fun collectInformation(file: PsiFile) = InType()

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean) = collectInformation(file)

    override fun doAnnotate(collectedInfo: InType?) = OutType()

    override fun apply(file: PsiFile, annotationResult: OutType?, holder: AnnotationHolder) {
        if (file !is ArendFile) {
            return
        }

        for (error in TypeCheckingService.getInstance(file.project).getErrors(file)) {
            val psi = error.cause as? ArendCompositeElement ?: continue
            val config = PrettyPrinterConfig.DEFAULT
            holder.createAnnotation(ArendHighlightingAnnotator.levelToSeverity(error.level), psi.textRange, error.shortMessage, DocStringBuilder.build(vHang(error.getShortHeaderDoc(config), error.getBodyDoc(config))))
        }
    }
}

class InType

class OutType
