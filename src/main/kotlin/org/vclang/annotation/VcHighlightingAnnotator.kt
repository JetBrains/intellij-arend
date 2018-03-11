package org.vclang.annotation

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.ProjectAndLibrariesScope
import com.intellij.psi.stubs.StubIndex
import org.vclang.highlight.VcHighlightingColors
import org.vclang.psi.*
import org.vclang.psi.ext.PsiReferable
import org.vclang.psi.ext.VcReferenceElement
import org.vclang.psi.stubs.index.VcDefinitionIndex
import org.vclang.quickfix.ResolveRefFixAction
import org.vclang.quickfix.ResolveRefQuickFix

class VcHighlightingAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element is VcReferenceElement) {
            val reference = element.reference
            if (reference != null) {
                val psiElement = reference.resolve()
                if (psiElement == null) {
                    val annotation = holder.createErrorAnnotation(element, "Unresolved reference")
                    annotation.highlightType = ProblemHighlightType.ERROR
                    val name = element.referenceName
                    val project = element.project
                    val scope = ProjectAndLibrariesScope(project)
                    val v = StubIndex.getElements(VcDefinitionIndex.KEY, name, project,scope, PsiReferable::class.java)
                    for (psi in v) {
                        annotation.registerFix(object: BaseIntentionAction(){
                            var decision : List<ResolveRefFixAction> = ResolveRefQuickFix.getDecision(psi, element)

                            override fun getFamilyName(): String {
                                return "vclang.reference.resolve"
                            }

                            override fun getText(): String {
                                return decision.toString()
                            }

                            override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
                                return decision.isNotEmpty()
                            }

                            override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {

                            }
                        })
                    }


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
