package org.arend.intention

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.arend.psi.*

class WrapInGoalIntention : SelfTargetingIntention<ArendExpr>(
        ArendExpr::class.java,
        "Wrap selected into a goal"
) {
    override fun isApplicableTo(element: ArendExpr, caretOffset: Int, editor: Editor): Boolean {
        if (PsiTreeUtil.hasErrorElements(element)) return false
        val selectedExpr = selectedExpr(editor, element) ?: return false
        return selectedExpr !is ArendGoal
    }

    override fun applyTo(element: ArendExpr, project: Project, editor: Editor) {
        val selectedExpr = selectedExpr(editor, element) ?: return
        WriteCommandAction.runWriteCommandAction(project) {
            val expr = ArendPsiFactory(project)
                    .createExpression("{?(${selectedExpr.text})}")
                    .linearDescendants
                    .last()
            selectedExpr.replace(expr)
        }
    }

    private fun selectedExpr(editor: Editor, element: ArendExpr): PsiElement? {
        val selected = EditorUtil.getSelectionInAnyMode(editor)
                .takeUnless { it.isEmpty }
                ?: element.textRange
        return element.ancestors.firstOrNull { selected in it.textRange }
    }
}