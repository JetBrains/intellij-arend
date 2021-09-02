package org.arend.intention

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import org.arend.psi.ArendGoal
import org.arend.refactoring.tryCorrespondedSubExpr
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.error.local.GoalError
import org.arend.util.ArendBundle

class GenerateFunctionFromGoalIntention : AbstractGenerateFunctionIntention() {

    override fun getText(): String = ArendBundle.message("arend.generate.function.from.goal")

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        editor ?: return false
        file ?: return false
        if (!canModify(file) || !BaseArendIntention.canModify(file)) {
            return false
        }
        return file.findElementAt(editor.caretModel.offset)?.parentOfType<ArendGoal>(false) != null
    }

    override fun extractSelectionData(file: PsiFile, editor: Editor, project: Project): SelectionResult? {
        val goal = file.findElementAt(editor.caretModel.offset)?.parentOfType<ArendGoal>(false) ?: return null
        val errorService = project.service<ErrorService>()
        val arendError = errorService.errors[goal.containingFile]?.firstOrNull { it.cause == goal }?.error as? GoalError
                ?: return null
        val goalType = (arendError as? GoalError)?.expectedType
        val goalExpr = goal.expr?.let {
            tryCorrespondedSubExpr(it.textRange, file, project, editor)
        }?.subCore
        return SelectionResult(goalType, goal, goal.textRange, null, goal.defIdentifier?.name, goalExpr)
    }
}