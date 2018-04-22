package org.vclang.annotation

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.ProjectAndLibrariesScope
import com.intellij.psi.stubs.StubIndex
import org.vclang.highlight.VcHighlightingColors
import org.vclang.psi.*
import org.vclang.psi.ext.PsiReferable
import org.vclang.psi.ext.VcReferenceElement
import org.vclang.psi.stubs.index.VcDefinitionIndex
import org.vclang.quickfix.ResolveRefQuickFix

class VcHighlightingAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element is VcReferenceElement) {
            val reference = element.reference
            if (reference != null) {
                val psiElement = reference.resolve()
                if (psiElement == null) {
                    val parent : PsiElement? = element.parent
                    val needsFix = parent !is VcLongName || element.prevSibling == null
                    val annotation = holder.createErrorAnnotation(element, "Unresolved reference")
                    annotation.highlightType = ProblemHighlightType.ERROR

                    if (needsFix) {
                        val project = element.project
                        val indexedDefinitions = StubIndex.getElements(VcDefinitionIndex.KEY, element.referenceName, project, ProjectAndLibrariesScope(project), PsiReferable::class.java)
                        val fixes = indexedDefinitions.map { ResolveRefQuickFix.getDecision(it, element) }.filterNotNull()
                        annotation.registerFix(VclangImportHintAction(fixes))
                    }

                }
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
