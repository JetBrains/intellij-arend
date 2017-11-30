package org.vclang.annotation

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import org.vclang.highlight.VcHighlightingColors
import org.vclang.psi.VcDefIdentifier
import org.vclang.psi.VcInfixArgument
import org.vclang.psi.VcPostfixArgument

class VcHighlightingAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val color = when (element) {
            is VcDefIdentifier -> VcHighlightingColors.DECLARATION
            is VcInfixArgument, is VcPostfixArgument -> VcHighlightingColors.OPERATORS
            else -> return
        }

        holder.createInfoAnnotation(element, null).textAttributes = color.textAttributesKey
    }
}
