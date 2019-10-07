package org.arend.quickfix.removers

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.psi.ArendClause
import org.arend.psi.deleteChildRangeWithNotification
import org.arend.psi.extendLeft

class RemovePatternRightHandSideQuickFix (private val clauseRef: SmartPsiElementPointer<ArendClause>) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = "arend.pattern"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = clauseRef.element != null

    override fun getText(): String = "Remove redundant clause's right-hand side"

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val clause = clauseRef.element ?: return
        val fatArrow = clause.fatArrow
        val expr = clause.expr
        if (fatArrow != null && expr != null) clause.deleteChildRangeWithNotification(fatArrow, expr)
    }
}