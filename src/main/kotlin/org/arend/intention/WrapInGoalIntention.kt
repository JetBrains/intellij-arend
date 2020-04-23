package org.arend.intention

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.arend.psi.ArendGoal
import org.arend.psi.ancestors
import org.arend.psi.linearDescendants

class WrapInGoalIntention : IntentionAction {
    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        val selectedExpr = selectedExpr(editor ?: return false, file) ?: return false
        return selectedExpr.linearDescendants.none { it is ArendGoal }
            && selectedExpr.ancestors.take(9).none { it is ArendGoal }
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        editor ?: return
        val selectedExpr = selectedExpr(editor, file) ?: return
        // It's better to use PsiElement's mutation API I believe
        val document = editor.document
        assert(document.isWritable)
        val textRange = selectedExpr.textRange
        val chars = document.immutableCharSequence
        if (chars[textRange.startOffset] == '(' && chars[textRange.endOffset - 1] == ')') {
            document.insertString(textRange.endOffset, "}")
            document.insertString(textRange.startOffset, "{?")
        } else {
            document.insertString(textRange.endOffset, ")}")
            document.insertString(textRange.startOffset, "{?(")
        }
    }

    override fun getText() = "Wrap selected into a goal"

    override fun getFamilyName() = text

    override fun startInWriteAction() = true

    private fun selectedExpr(editor: Editor, file: PsiFile?): PsiElement? {
        val element = file?.findElementAt(editor.caretModel.offset)
            ?: return null
        val selected = EditorUtil.getSelectionInAnyMode(editor)
            .takeUnless { it.isEmpty }
            ?: element.textRange
        return element.ancestors.firstOrNull { selected in it.textRange }
    }
}