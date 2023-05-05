package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.psi.ArendPsiFactory
import org.arend.psi.childOfType
import org.arend.psi.ext.ArendCaseExpr
import org.arend.psi.ext.ArendReturnExpr
import org.arend.refactoring.moveCaretToEndOffset
import org.arend.util.ArendBundle

class AddReturnKeywordQuickFix(private val cause: SmartPsiElementPointer<PsiElement>) : IntentionAction {

    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = ArendBundle.message("arend.return.add")

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = cause.element != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val arendCaseExpr = cause.element as? ArendCaseExpr ?: return
        val psiFactory = ArendPsiFactory(project)
        updateReturnExpression(psiFactory, arendCaseExpr)
        moveCaretToEndOffset(editor, arendCaseExpr.childOfType<ArendReturnExpr>()!!)
    }
}
