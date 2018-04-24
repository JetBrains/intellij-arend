package org.vclang.annotation

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.vclang.highlight.VcHighlightingColors
import org.vclang.psi.*
import org.vclang.psi.ext.VcReferenceElement

class VcHighlightingAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element is VcReferenceElement) {
            if (VclangImportHintAction.referenceUnresolved(element)) {
                val annotation = holder.createErrorAnnotation(element, "Unresolved reference")
                annotation.highlightType = ProblemHighlightType.ERROR

                val fix = VclangImportHintAction(element)
                if (fix.isAvailable(element.project, null, element.containingFile))
                    annotation.registerFix(fix)
            }
        }

        val color = when {
            element is VcDefIdentifier -> VcHighlightingColors.DECLARATION
            element is VcInfixArgument || element is VcPostfixArgument -> VcHighlightingColors.OPERATORS
            element is VcRefIdentifier || element is LeafPsiElement && element.node.elementType == VcElementTypes.DOT -> {
                val parent = element.parent as? VcLongName ?: return
                if (parent.parent is VcStatCmd) return
                val refList = parent.refIdentifierList
                if (refList.indexOf(element) == refList.size - 1) return
                VcHighlightingColors.LONG_NAME
            }
            else -> return
        }


        holder.createInfoAnnotation(element, null).textAttributes = color.textAttributesKey
    }
}
