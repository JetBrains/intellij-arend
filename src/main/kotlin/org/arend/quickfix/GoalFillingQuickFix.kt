package org.arend.quickfix

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.arend.psi.ArendGoal
import org.arend.refactoring.replaceExprSmart
import org.arend.term.concrete.Concrete
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.error.local.GoalError
import org.arend.typechecking.visitor.CheckTypeVisitor

class GoalFillingQuickFix(private val element: ArendGoal, private val goal: GoalError) : IntentionAction {
    override fun invoke(project: Project, editor: Editor, file: PsiFile?) {
        if (goal.goalSolver != null) {
            val concrete = if (goal.result != null) goal.result else {
                val concrete =
                    if (goal.typecheckingContext == null) null
                    else goal.goalSolver.trySolve(CheckTypeVisitor.loadTypecheckingContext(goal.typecheckingContext, project.service<TypeCheckingService>().typecheckerState, project.service<ErrorService>()), goal.causeSourceNode, goal.expectedType)
                if (concrete == null) {
                    ApplicationManager.getApplication().invokeLater {
                        HintManager.getInstance().showErrorHint(editor, "Cannot solve goal")
                    }
                    return
                }
                if (concrete !is Concrete.Expression) throw IllegalArgumentException()
                concrete
            }

            val text = concrete.toString()
            ApplicationManager.getApplication().runWriteAction {
                replaceExprSmart(editor.document, element, null, element.textRange, null, concrete, text)
            }
        } else {
            val expr = element.expr ?: return
            ApplicationManager.getApplication().runWriteAction {
                replaceExprSmart(editor.document, element, null, element.textRange, expr, null, expr.text)
            }
        }
    }

    override fun startInWriteAction() = false

    override fun getElementToMakeWritable(currentFile: PsiFile) = currentFile

    override fun getFamilyName() = "arend.goal"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) =
        element.isValid && (goal.result != null || goal.goalSolver?.willTrySolve(goal.causeSourceNode, goal.expectedType) == true)

    override fun getText() = if (goal.result != null) "Fill goal" else "Solve goal"
}