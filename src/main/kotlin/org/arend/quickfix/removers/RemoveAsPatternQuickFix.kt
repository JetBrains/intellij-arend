package org.arend.quickfix.removers

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.arend.psi.ArendAsPattern
import org.arend.psi.ArendPattern
import org.arend.psi.deleteWithNotification
import org.arend.refactoring.deleteSuperfluousPatternParentheses

class RemoveAsPatternQuickFix (private val asPattern: ArendAsPattern): IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = "arend.pattern"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true

    override fun getText(): String = "Remove \\as-pattern"

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val pattern = asPattern.parent as? ArendPattern
        val atomPattern = pattern?.atomPattern
        asPattern.deleteWithNotification()
        if (atomPattern != null) deleteSuperfluousPatternParentheses(atomPattern)
    }

}