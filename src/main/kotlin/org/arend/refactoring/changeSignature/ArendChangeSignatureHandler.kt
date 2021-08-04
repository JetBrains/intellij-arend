package org.arend.refactoring.changeSignature

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.changeSignature.ChangeSignatureHandler
import org.arend.psi.ArendDefFunction
import org.arend.psi.ancestor
import org.arend.util.ArendBundle

class ArendChangeSignatureHandler : ChangeSignatureHandler {
    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        val function = elements.singleOrNull() as? ArendDefFunction ?: return
        showRefactoringDialog(project, function)
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext?) {
        TODO("Not yet implemented")
    }

    override fun findTargetMember(element: PsiElement) = element.ancestor<ArendDefFunction>()

    override fun getTargetNotFoundMessage() = ArendBundle.message("arend.error.wrongCaretPosition")

    private fun showRefactoringDialog(project: Project, function: ArendDefFunction) {
        val descriptor = ArendSignatureDescriptor(function)
        ArendChangeSignatureDialog(project, descriptor).show()
    }

}