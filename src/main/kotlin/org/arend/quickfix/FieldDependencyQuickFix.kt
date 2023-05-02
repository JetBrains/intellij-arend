package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.psi.ArendPsiFactory
import org.arend.psi.ext.ArendLocalCoClause
import org.arend.refactoring.moveCaretToEndOffset
import org.arend.typechecking.error.local.FieldDependencyError
import org.arend.util.ArendBundle

class FieldDependencyQuickFix(
    private val cause: SmartPsiElementPointer<PsiElement>,
    private val error: FieldDependencyError
) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = ArendBundle.message("arend.field.dependency")

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = cause.element != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val element = cause.element
        val expression = element?.parent ?: return

        val psiFactory = ArendPsiFactory(project)
        var localCoClause = psiFactory.createLocalCoClause(error.field.toString())
        val whiteSpace = psiFactory.createWhitespace(" ")

        localCoClause = expression.addAfter(localCoClause, element) as ArendLocalCoClause
        expression.addBefore(whiteSpace, localCoClause)

        moveCaretToEndOffset(editor, localCoClause)
    }
}
