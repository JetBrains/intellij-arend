package org.arend.quickfix.removers

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.psi.deleteWithWhitespaces
import org.arend.psi.ext.ArendTruncatedUniverseAppExpr
import org.arend.psi.prevElement
import org.arend.util.ArendBundle

class RemoveTruncatedUniverseQuickFix(private val cause: SmartPsiElementPointer<ArendTruncatedUniverseAppExpr>) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = ArendBundle.message("arend.universe.remove")

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = cause.element != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val universe = cause.element
        universe?.prevElement?.prevElement?.deleteWithWhitespaces()
        universe?.delete()
    }
}
