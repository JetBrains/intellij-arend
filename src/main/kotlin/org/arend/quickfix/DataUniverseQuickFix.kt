package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.psi.ArendPsiFactory
import org.arend.psi.childOfType
import org.arend.psi.ext.ArendUniverseExpr
import org.arend.typechecking.error.local.DataUniverseError
import org.arend.util.ArendBundle

class DataUniverseQuickFix(
        private val cause: SmartPsiElementPointer<PsiElement>,
        private val error: DataUniverseError
) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = ArendBundle.message("arend.universe.replace")

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = cause.element != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val psiFactory = ArendPsiFactory(project)
        val actualSort = psiFactory.createFromText("\\data D : ${error.actualSort}")?.childOfType<ArendUniverseExpr>()!!

        cause.element?.parent?.replace(actualSort)
    }
}
