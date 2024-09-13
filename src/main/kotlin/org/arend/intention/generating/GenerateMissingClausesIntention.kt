package org.arend.intention.generating

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.arend.intention.SelfTargetingIntention
import org.arend.psi.ArendFile
import org.arend.util.ArendBundle

class GenerateMissingClausesIntention : SelfTargetingIntention<PsiElement>(PsiElement::class.java, ArendBundle.message("arend.generatePatternMatchingClauses")) {
    override fun isApplicableTo(element: PsiElement, caretOffset: Int, editor: Editor): Boolean {
        return checkMissingClauses(element)
    }

    override fun applyTo(element: PsiElement, project: Project, editor: Editor) {
        val file = element.containingFile as ArendFile
        val (group, startOffsetParent) = deleteFunctionBody(element) ?: return

        fixMissingClausesError(project, file, editor, group, startOffsetParent - 1)
    }

    override fun startInWriteAction(): Boolean = false
}
