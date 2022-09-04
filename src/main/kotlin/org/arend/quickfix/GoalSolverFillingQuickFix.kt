package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.arend.extImpl.definitionRenamer.CachingDefinitionRenamer
import org.arend.psi.ext.ArendExpr
import org.arend.refactoring.PsiLocatedRenamer
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.DefinitionRenamerConcreteVisitor
import org.arend.typechecking.error.local.GoalError
import org.arend.util.ArendBundle

class GoalSolverFillingQuickFix(private val element: ArendExpr,
                                private val goal: GoalError,
                                private val action: (Editor, Concrete.Expression, String) -> Unit)
    : IntentionAction {
    override fun invoke(project: Project, editor: Editor, file: PsiFile?) {
        val definitionRenamer = PsiLocatedRenamer(element)
        val text = goal.result.accept(DefinitionRenamerConcreteVisitor(CachingDefinitionRenamer(definitionRenamer)), null).toString()
        action(editor, goal.result, text)
        definitionRenamer.writeAllImportCommands()
    }

    override fun startInWriteAction() = true

    override fun getFamilyName() = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) =
        element.isValid && goal.result != null

    override fun getText() = ArendBundle.message("arend.expression.fillGoal")
}
