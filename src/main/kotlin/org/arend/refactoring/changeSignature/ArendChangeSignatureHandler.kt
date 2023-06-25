package org.arend.refactoring.changeSignature

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.changeSignature.ChangeSignatureHandler
import org.arend.psi.ancestor
import org.arend.psi.ext.*
import org.arend.refactoring.changeSignature.ArendChangeInfo.Companion.getChangeInfoProcessorClass
import org.arend.term.abs.Abstract
import org.arend.util.ArendBundle

class ArendChangeSignatureHandler : ChangeSignatureHandler {
    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        val psi = elements.singleOrNull() as? Abstract.ParametersHolder ?: return
        getChangeInfoProcessorClass(psi) ?: return
        if (psi !is PsiLocatedReferable) return

        ArendChangeSignatureDialog(project, ArendChangeSignatureDescriptor(psi)).show()
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext?) {
        TODO("Not yet implemented")
    }

    override fun findTargetMember(element: PsiElement) = element.ancestor<PsiLocatedReferable>()

    override fun getTargetNotFoundMessage() = ArendBundle.message("arend.error.wrongCaretPosition")
}