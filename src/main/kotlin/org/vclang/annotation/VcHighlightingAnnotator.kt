package org.vclang.annotation

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import org.vclang.highlight.VcHighlightingColors
import org.vclang.psi.VcDefIdentifier
import org.vclang.psi.VcInfixName
import org.vclang.psi.VcTele

class VcHighlightingAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val color = when {
            element is VcDefIdentifier -> VcHighlightingColors.DECLARATION
            element is VcInfixName -> VcHighlightingColors.OPERATORS
            element is VcTele && element.lbrace != null -> VcHighlightingColors.IMPLICIT
            else -> return
        }

        holder.createInfoAnnotation(element, null).textAttributes = color.textAttributesKey
    }
}
