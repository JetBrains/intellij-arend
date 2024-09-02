package org.arend.intention.generating

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.suggested.endOffset
import org.arend.intention.SelfTargetingIntention
import org.arend.psi.ArendFile
import org.arend.psi.ArendPsiFactory
import org.arend.util.ArendBundle

class GenerateElimMissingClausesIntention : SelfTargetingIntention<PsiElement>(PsiElement::class.java, ArendBundle.message("arend.generateElimPatternMatchingClauses")) {
    override fun isApplicableTo(element: PsiElement, caretOffset: Int, editor: Editor): Boolean {
        return checkMissingClauses(element, editor)
    }

    override fun applyTo(element: PsiElement, project: Project, editor: Editor) {
        val file = element.containingFile as ArendFile
        val (group, _) = deleteFunctionBody(element) ?: return

        val psiFactory = ArendPsiFactory(project)
        val elim = psiFactory.createElim(emptyList())
        runWriteAction {
            group.add(psiFactory.createWhitespace(" "))
            group.add(elim)
        }

        val offset = group.endOffset
        runWriteAction {
            editor.document.insertString(offset, " ")
        }
        editor.caretModel.moveToOffset(offset + 1)

        val builder = TemplateBuilderImpl(elim)
        builder.replaceElement(elim, "")
        val template = builder.buildTemplate()
        TemplateManager.getInstance(project).startTemplate(editor, template, object : TemplateEditingAdapter() {
            override fun beforeTemplateFinished(state: TemplateState, template: Template?) {
                fixMissingClausesError(project, file, editor, group, state.currentVariableRange?.endOffset ?: return)
            }
        })
    }

    override fun startInWriteAction(): Boolean = false
}
