package org.arend.quickfix.removers

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.psi.ext.ArendClause
import org.arend.util.ArendBundle

class RemovePatternRightHandSideQuickFix (private val clauseRef: SmartPsiElementPointer<ArendClause>) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = clauseRef.element != null

    override fun getText(): String = ArendBundle.message("arend.clause.removeRedundantRHS")

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val clause = clauseRef.element ?: return
        val fatArrow = clause.fatArrow
        val expr = clause.expression
        if (fatArrow != null && expr != null) clause.deleteChildRange(fatArrow, expr)
    }
}