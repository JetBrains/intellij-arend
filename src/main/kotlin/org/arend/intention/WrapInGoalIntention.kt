package org.arend.intention

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.arend.psi.ext.ArendExpr
import org.arend.psi.ArendFile
import org.arend.psi.ext.ArendLiteral
import org.arend.util.ArendBundle

class WrapInGoalIntention : SelectionIntention<ArendExpr>(ArendExpr::class.java, ArendBundle.message("arend.expression.wrapInGoal")) {
    override fun isAvailable(project: Project, editor: Editor, file: ArendFile, element: ArendExpr) =
        (element as? ArendLiteral)?.goal == null

    override fun invoke(project: Project, editor: Editor, file: ArendFile, element: ArendExpr, selected: TextRange) {
        // It's better to use PsiElement's mutation API I believe
        val document = editor.document
        assert(document.isWritable)
        val textRange = element.textRange
        val chars = document.immutableCharSequence
        if (chars[textRange.startOffset] == '(' && chars[textRange.endOffset - 1] == ')') {
            document.insertString(textRange.endOffset, "}")
            document.insertString(textRange.startOffset, "{?")
        } else {
            document.insertString(textRange.endOffset, ")}")
            document.insertString(textRange.startOffset, "{?(")
        }
    }
}