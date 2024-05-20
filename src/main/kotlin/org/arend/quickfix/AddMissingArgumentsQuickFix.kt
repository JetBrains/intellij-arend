package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.addSiblingAfter
import org.arend.ext.error.MissingArgumentsError
import org.arend.psi.ArendPsiFactory
import org.arend.psi.descendantOfType
import org.arend.psi.ext.ArendAtomArgument
import org.arend.psi.ext.ArendAtomFieldsAcc
import org.arend.util.ArendBundle

class AddMissingArgumentsQuickFix(private val error: MissingArgumentsError, private val cause: SmartPsiElementPointer<PsiElement>) : IntentionAction {

    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = ArendBundle.message("arend.add.missingExplicitArguments")

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        var element = cause.element
        while (element !is ArendAtomFieldsAcc) {
            element = element?.parent
            if (element == null) {
                return false
            }
        }
        return true
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        var element = cause.element
        while (element !is ArendAtomFieldsAcc) {
            element = element?.parent
            if (element == null) {
                return
            }
        }

        val psiFactory = ArendPsiFactory(project)
        val tgoal = psiFactory.createExpression("foo {?}").descendantOfType<ArendAtomArgument>()!!
        val space = psiFactory.createWhitespace(" ")
        for (arg in 0 until error.numberOfArgs) {
            element.addSiblingAfter(tgoal)
            element.addSiblingAfter(space)
        }
    }
}
