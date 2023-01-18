package org.arend.quickfix.removers

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.psi.ext.ArendAsPattern
import org.arend.psi.ext.ArendPattern
import org.arend.refactoring.deleteSuperfluousPatternParentheses
import org.arend.util.ArendBundle

class RemoveAsPatternQuickFix (private val asPatternRef: SmartPsiElementPointer<ArendAsPattern>): IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = asPatternRef.element != null

    override fun getText(): String = ArendBundle.message("arend.pattern.removeAs")

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val asPattern = asPatternRef.element ?: return
        var pattern = asPattern.parent as? ArendPattern ?: return
        if (pattern.sequence.size == 1) {
            pattern = pattern.replace(pattern.sequence.single()) as ArendPattern
        } else {
            asPattern.delete()
        }
        deleteSuperfluousPatternParentheses(pattern)
    }

}