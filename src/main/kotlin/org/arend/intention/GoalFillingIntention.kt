package org.arend.intention

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.arend.ext.error.GeneralError
import org.arend.psi.ArendGoal
import org.arend.refactoring.replaceExprSmart
import org.arend.toolWindow.errors.tree.selectedArendErrors
import org.arend.typechecking.error.local.GoalError

class GoalFillingIntention : SelfTargetingIntention<ArendGoal>(ArendGoal::class.java,
        "Fill goal with expression inside") {
    // To fill goal, there need to be an expression inside of it
    override fun isApplicableTo(element: ArendGoal, caretOffset: Int, editor: Editor) = element.expr != null &&
            selectedGoal(element.project, editor)?.errors?.all { it.level != GeneralError.Level.ERROR } == true

    override fun applyTo(element: ArendGoal, project: Project, editor: Editor) {
        val goal = selectedGoal(project, editor) ?: return
        if (goal.errors.any { it.level == GeneralError.Level.ERROR }) return
        val expr = element.expr ?: return
        WriteCommandAction.runWriteCommandAction(project) {
            replaceExprSmart(editor.document, element, element.textRange, expr.text)
        }
    }

    private fun selectedGoal(project: Project, editor: Editor) = selectedArendErrors(project, editor)
            .map { it.error }
            .filterIsInstance<GoalError>()
            .firstOrNull()
}