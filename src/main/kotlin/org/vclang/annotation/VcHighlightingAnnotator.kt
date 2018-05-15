package org.vclang.annotation

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.jetbrains.jetpad.vclang.error.Error
import com.jetbrains.jetpad.vclang.naming.reference.LocatedReferable
import com.jetbrains.jetpad.vclang.naming.resolving.DuplicateNameChecker
import com.jetbrains.jetpad.vclang.term.group.Group
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

        if (element is Group) {
            object : DuplicateNameChecker() {
                override fun duplicateName(ref1: LocatedReferable, ref2: LocatedReferable, level: Error.Level) {
                    annotateDuplicateName(ref1, level, holder)
                    annotateDuplicateName(ref2, level, holder)
                }
            }.checkGroup(element)
            return
        }

        val color = when {
            element is VcDefIdentifier -> VcHighlightingColors.DECLARATION
            element is VcInfixArgument || element is VcPostfixArgument -> VcHighlightingColors.OPERATORS
            element is VcRefIdentifier || element is LeafPsiElement && element.node.elementType == VcElementTypes.DOT -> {
                val parent = element.parent as? VcLongName ?: return
                if (parent.parent is VcStatCmd) return
                val refList = parent.refIdentifierList
                if (!refList.isEmpty() && refList.last() == element) return
                VcHighlightingColors.LONG_NAME
            }
            else -> return
        }

        holder.createInfoAnnotation(element, null).textAttributes = color.textAttributesKey
    }

    private fun annotateDuplicateName(ref: LocatedReferable, level: Error.Level, holder: AnnotationHolder) {
        var psiRef = ref as? PsiElement ?: return
        if (psiRef is VcDefinition) {
            psiRef = psiRef.children.first { it is VcDefIdentifier } ?: psiRef
        }
        holder.createAnnotation(levelToSeverity(level), psiRef.textRange, "Duplicate definition name")
    }

    private fun levelToSeverity(level: Error.Level) = when (level) {
        Error.Level.ERROR -> HighlightSeverity.ERROR
        Error.Level.WARNING -> HighlightSeverity.WARNING
        Error.Level.GOAL -> HighlightSeverity.WEAK_WARNING
        Error.Level.INFO -> HighlightSeverity.INFORMATION
    }
}
