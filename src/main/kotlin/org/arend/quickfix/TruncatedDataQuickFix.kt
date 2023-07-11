package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.core.sort.Sort
import org.arend.psi.ArendPsiFactory
import org.arend.psi.ext.ArendDefFunction
import org.arend.typechecking.error.local.TruncatedDataError
import org.arend.util.ArendBundle

class TruncatedDataQuickFix(
    private val cause: SmartPsiElementPointer<PsiElement>,
    private val error: TruncatedDataError
) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = ArendBundle.message("arend.truncatedData.changeKeyword")

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = cause.element != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        var element = cause.element
        while (element !is ArendDefFunction) {
            element = element?.parent
            if (element == null) {
                return
            }
        }

        val psiFactory = ArendPsiFactory(project)
        val lemmaKeyword = psiFactory.createFunctionKeyword("\\lemma")
        val sfuncKeyword = psiFactory.createFunctionKeyword("\\sfunc")

        if (error.dataDef.sort == Sort.PROP) {
            element.functionKw.replace(lemmaKeyword)
        } else {
            element.functionKw.replace(sfuncKeyword)
        }
    }
}
