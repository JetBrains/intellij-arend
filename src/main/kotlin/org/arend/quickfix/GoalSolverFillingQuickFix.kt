package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.arend.core.expr.visitor.ScopeDefinitionRenamer
import org.arend.naming.scope.ConvertingScope
import org.arend.psi.ArendExpr
import org.arend.refactoring.replaceExprSmart
import org.arend.term.prettyprint.DefinitionRenamerConcreteVisitor
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.error.local.GoalError

class GoalSolverFillingQuickFix(private val element: ArendExpr, private val goal: GoalError) : IntentionAction {
    override fun invoke(project: Project, editor: Editor, file: PsiFile?) {
        val text = goal.result.accept(DefinitionRenamerConcreteVisitor(ScopeDefinitionRenamer(ConvertingScope(project.service<TypeCheckingService>().newReferableConverter(false), element.scope))), null).toString()
        replaceExprSmart(editor.document, element, null, element.textRange, null, goal.result, text)
    }

    override fun startInWriteAction() = true

    override fun getFamilyName() = "arend.goal"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) =
        element.isValid && goal.result != null

    override fun getText() = "Fill goal"
}
