package org.arend.refactoring.changeSignature

import com.intellij.concurrency.resetThreadContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.changeSignature.ChangeSignatureHandler
import org.arend.codeInsight.ArendCodeInsightUtils
import org.arend.psi.ancestor
import org.arend.psi.ext.*
import org.arend.refactoring.changeSignature.ArendChangeInfo.Companion.getDefinitionsWithExternalParameters
import org.arend.term.abs.Abstract
import org.arend.util.ArendBundle

class ArendChangeSignatureHandler : ChangeSignatureHandler {
    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        val psi = elements.singleOrNull() as? Abstract.ParametersHolder ?: return
        if (psi !is ArendFunctionDefinition<*> && psi !is ArendClassField && psi !is ArendDefData && psi !is ArendDefClass && psi !is ArendConstructor) return
        if (psi !is PsiLocatedReferable) return

        resetThreadContext().use { token ->
            ArendChangeSignatureDialog(project, ArendChangeSignatureDescriptor(psi)).show()
        }
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext?) {
        TODO("Not yet implemented")
    }

    override fun findTargetMember(element: PsiElement) = element.ancestor<PsiLocatedReferable>()

    override fun getTargetNotFoundMessage() = ArendBundle.message("arend.error.wrongCaretPosition")

    companion object {
        fun checkExternalParametersOk(psi: Abstract.ParametersHolder): Boolean {
            val definitionsWithExternalParameters = getDefinitionsWithExternalParameters(psi)
            val problematicDefinitions = ArrayList<PsiLocatedReferable>()

            for (def in definitionsWithExternalParameters) {
                val implicitParams = ArendCodeInsightUtils.getExternalParameters(def)
                if (implicitParams == null) problematicDefinitions.add(def)
            }

            if (problematicDefinitions.isNotEmpty()) {
                if (ApplicationManager.getApplication().isUnitTestMode || Messages.showYesNoDialog(ArendBundle.message("arend.refactoring.notTypecheckedMessage.partI." + if (problematicDefinitions.size > 1) "multiple" else "single",
                        problematicDefinitions.map { "`${it.name}`" }.fold("") { acc: String, s: String -> "$acc${if (acc.isNotEmpty()) ", " else ""}$s" }) + ArendBundle.message("arend.refactoring.notTypecheckedMessage.partII"),
                        RefactoringBundle.message("changeSignature.refactoring.name"),
                        Messages.getQuestionIcon()) == Messages.NO)
                    return false
            }

            return true
        }
    }
}