package org.arend.quickfix.removers

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.elementType
import org.arend.psi.ArendElementTypes.PIPE
import org.arend.psi.ext.ArendFunctionBody
import org.arend.psi.ext.CoClauseBase
import org.arend.psi.findPrevSibling
import org.arend.refactoring.moveCaretToStartOffset
import org.arend.util.ArendBundle

class RemoveCoClauseQuickFix(private val coClauseRef: SmartPsiElementPointer<CoClauseBase>) : IntentionAction {
    override fun startInWriteAction() = true

    override fun getFamilyName() = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = coClauseRef.element != null

    override fun getText() = ArendBundle.message("arend.coClause.removeRedundant")

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val coClause = coClauseRef.element ?: return
        moveCaretToStartOffset(editor, coClause)
        val pipe = coClause.findPrevSibling()
        val parent = coClause.parent
        if (pipe != null && pipe.elementType == PIPE) {
            parent.deleteChildRange(pipe, coClause)
        } else coClause.delete()

        if (parent is ArendFunctionBody && parent.firstChild == null) parent.delete()
    }
}