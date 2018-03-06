package org.vclang.annotation

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import org.vclang.highlight.VcHighlightingColors
import org.vclang.psi.VcDefIdentifier
import org.vclang.psi.VcInfixArgument
import org.vclang.psi.VcPostfixArgument
import org.vclang.psi.ext.VcCompositeElement

class VcHighlightingAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element is VcCompositeElement) {
            val reference = element.reference
            if (reference != null) {
                val psiElement = reference.resolve()
                if (psiElement == null) {
                    holder.createErrorAnnotation(element, "Unresolved reference").highlightType = ProblemHighlightType.ERROR
                }
            }
        }

        val color = when (element) {
            is VcDefIdentifier -> VcHighlightingColors.DECLARATION
            is VcInfixArgument, is VcPostfixArgument -> VcHighlightingColors.OPERATORS
            else -> return
        }


        holder.createInfoAnnotation(element, null).textAttributes = color.textAttributesKey
    }
}
