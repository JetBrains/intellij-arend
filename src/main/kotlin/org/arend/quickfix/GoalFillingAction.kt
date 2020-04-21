package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.arend.ext.error.GeneralError
import org.arend.psi.ArendGoal
import org.arend.refactoring.replaceExprSmart
import org.arend.typechecking.error.local.GoalError

class GoalFillingAction(private val element: ArendGoal, private val goal: GoalError) : IntentionAction {
    override fun invoke(project: Project, editor: Editor, file: PsiFile?) {
        if (goal.errors.any { it.level == GeneralError.Level.ERROR }) return
        val expr = element.expr ?: return
        replaceExprSmart(editor.document, element, element.textRange, expr.text)
    }

    override fun startInWriteAction() = true

    override fun getFamilyName() = "arend.goal"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = element.isValid

    override fun getText() = "Fill goal with expression inside"
}