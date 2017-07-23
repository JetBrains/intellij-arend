package org.vclang.ide.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import org.vclang.ide.colors.VcHighlightingColors
import org.vclang.lang.core.psi.VcDefinition
import org.vclang.lang.core.psi.VcIdentifier
import org.vclang.lang.core.psi.VcTele

class VcHighlightingAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val color = when {
            element is VcIdentifier && element.parent?.parent is VcDefinition ->
                VcHighlightingColors.DECLARATION
            element is VcTele && element.lbrace != null ->
                VcHighlightingColors.IMPLICIT
            else -> return
        }

        holder.createInfoAnnotation(element, null).textAttributes = color.textAttributesKey
    }
}
