package org.vclang.annotation

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import org.vclang.highlight.VcHighlightingColors
import org.vclang.psi.*

class VcHighlightingAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val color = when (element) {
            is VcDefIdentifier -> VcHighlightingColors.DECLARATION
            is VcInfixName -> VcHighlightingColors.OPERATORS
            else -> return
        }

        holder.createInfoAnnotation(element, null).textAttributes = color.textAttributesKey
    }
}
