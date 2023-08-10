package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.psi.ArendPsiFactory
import org.arend.psi.descendantOfType
import org.arend.psi.ext.ArendArgumentAppExpr
import org.arend.psi.ext.ArendAtomArgument
import org.arend.psi.ext.ArendImplicitArgument
import org.arend.util.ArendBundle

class ExplicitnessQuickFix(private val cause: SmartPsiElementPointer<PsiElement>) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = ArendBundle.message("arend.argument.explicitness")

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = cause.element != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        var element = cause.element
        val isArendArgumentAppExpr = element is ArendArgumentAppExpr
        val text = element?.text

        while (element !is ArendImplicitArgument) {
            element = element?.parent
            if (element == null) {
                return
            }
        }

        val psiFactory = ArendPsiFactory(project)
        val atom = psiFactory.createExpression(
            if (isArendArgumentAppExpr) "foo ($text)"
            else "foo $text"
        ).descendantOfType<ArendAtomArgument>()!!

        element.replace(atom)
    }
}
