package org.arend.quickfix.removers

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.arend.psi.ArendCoClause
import org.arend.psi.ArendFunctionBody
import org.arend.psi.ArendInstanceBody
import org.arend.psi.deleteWithNotification
import org.arend.refactoring.moveCaretToStartOffset

class RemoveCoClauseQuickFix(private val coClause: ArendCoClause) : IntentionAction {
    override fun startInWriteAction() = true

    override fun getFamilyName() = "arend.instance"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = true

    override fun getText() = "Remove redundant coclause"

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        moveCaretToStartOffset(editor, coClause)
        val parent = coClause.parent
        coClause.deleteWithNotification()
        if ((parent is ArendFunctionBody || parent is ArendInstanceBody) && parent.firstChild == null) parent.deleteWithNotification()
    }
}